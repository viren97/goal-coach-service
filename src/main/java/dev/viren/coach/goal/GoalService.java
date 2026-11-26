package dev.viren.coach.goal;

import dev.viren.coach.command.Commands.GoalCommand;
import dev.viren.coach.common.Hashing;
import dev.viren.coach.common.error.CoachException;
import dev.viren.coach.common.error.ErrorCode;
import dev.viren.coach.config.CoachProperties;
import dev.viren.coach.nudge.NudgeTiming;
import dev.viren.coach.outbox.OutboxEventType;
import dev.viren.coach.outbox.OutboxService;
import dev.viren.coach.progress.ProgressCache;
import dev.viren.coach.task.DifficultyPolicy;
import dev.viren.coach.user.User;
import dev.viren.coach.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {
    private final GoalRepository goals;
    private final MilestoneRepository milestones;
    private final UserService users;
    private final OutboxService outbox;
    private final DifficultyPolicy difficulty;
    private final NudgeTiming timing;
    private final ProgressCache progressCache;
    private final CoachProperties.Goals config;
    private final Clock clock;

    public GoalService(GoalRepository goals, MilestoneRepository milestones, UserService users, OutboxService outbox,
                       DifficultyPolicy difficulty, NudgeTiming timing, ProgressCache progressCache,
                       CoachProperties properties, Clock clock) {
        this.goals = goals;
        this.milestones = milestones;
        this.users = users;
        this.outbox = outbox;
        this.difficulty = difficulty;
        this.timing = timing;
        this.progressCache = progressCache;
        this.config = properties.goals();
        this.clock = clock;
    }

    public record Submission(Goal goal, boolean created) {}

    public enum CheckInAnswer { EASIER, KEEP, PAUSE }

    /**
     * Accepts a goal and queues plan generation (goal row + GENERATE_PLAN outbox row in one transaction).
     * Same Idempotency-Key + same body replays the original goal; same key + different body is rejected.
     */
    @Transactional
    public Submission create(String userId, String idempotencyKey, String identity, String context) {
        User user = users.get(userId);
        String cleanIdentity = identity.strip();
        String cleanContext = context == null || context.isBlank() ? null : context.strip();
        String hash = Hashing.sha256(userId + "\n" + cleanIdentity + "\n" + (cleanContext == null ? "" : cleanContext));

        var existing = goals.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) {
                throw new CoachException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency-Key was already used with a different request");
            }
            return new Submission(existing.get(), false);
        }
        long open = goals.countByUserIdAndStatusIn(user.getId(), EnumSet.of(GoalStatus.PLANNING, GoalStatus.ACTIVE, GoalStatus.PAUSED));
        if (open >= config.maxActivePerUser()) {
            throw new CoachException(ErrorCode.GOAL_LIMIT_REACHED,
                    "You can have " + config.maxActivePerUser() + " open goal(s) at a time; complete or archive one first");
        }
        Goal goal = goals.save(new Goal(user.getId(), cleanIdentity, cleanContext, idempotencyKey, hash,
                difficulty.start(), clock.instant()));
        outbox.record(OutboxEventType.GENERATE_PLAN, goal.getId(), new GoalCommand(goal.getId()));
        return new Submission(goal, true);
    }

    @Transactional(readOnly = true)
    public Goal get(String goalId) {
        return goals.findById(goalId).orElseThrow(() -> CoachException.notFound("Goal", goalId));
    }

    @Transactional(readOnly = true)
    public List<Goal> forUser(String userId) {
        users.get(userId);
        return goals.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Milestone> milestones(String goalId) {
        return milestones.findByGoalIdOrderByPositionAsc(goalId);
    }

    @Transactional
    public Goal pause(String goalId) {
        Goal goal = get(goalId);
        goal.pause(clock.instant());
        progressCache.evict(goalId);
        return goal;
    }

    @Transactional
    public Goal resume(String goalId) {
        Goal goal = get(goalId);
        Instant now = clock.instant();
        goal.resume(now, now); // first task right away after a break
        progressCache.evict(goalId);
        return goal;
    }

    /** Answer to the fatigue check-in push. */
    @Transactional
    public Goal checkIn(String goalId, CheckInAnswer answer) {
        Goal goal = get(goalId);
        Instant now = clock.instant();
        switch (answer) {
            case EASIER -> goal.applyDifficulty(difficulty.onEasier(goal.difficultyState()), now);
            case KEEP -> goal.applyDifficulty(difficulty.onEngaged(goal.difficultyState()), now);
            case PAUSE -> {
                goal.pause(now);
                progressCache.evict(goalId);
                return goal;
            }
        }
        if (goal.isActive()) {
            goal.scheduleNextNudge(timing.next(goal, users.get(goal.getUserId()), now), now);
        }
        progressCache.evict(goalId);
        return goal;
    }
}
