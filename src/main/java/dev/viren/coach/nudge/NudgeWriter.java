package dev.viren.coach.nudge;

import dev.viren.coach.command.Commands.TaskCommand;
import dev.viren.coach.command.Commands.TopUpCommand;
import dev.viren.coach.config.CoachProperties;
import dev.viren.coach.goal.Goal;
import dev.viren.coach.goal.GoalRepository;
import dev.viren.coach.goal.Milestone;
import dev.viren.coach.goal.MilestoneRepository;
import dev.viren.coach.goal.MilestoneStatus;
import dev.viren.coach.outbox.OutboxEventType;
import dev.viren.coach.outbox.OutboxService;
import dev.viren.coach.task.CoachTask;
import dev.viren.coach.task.TaskRepository;
import dev.viren.coach.task.TaskStatus;
import dev.viren.coach.user.User;
import dev.viren.coach.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Transactional steps of the nudge pipeline. Each one commits its state change together with its outbox row. */
@Component
public class NudgeWriter {
    private final GoalRepository goals;
    private final MilestoneRepository milestones;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final TaskSelector selector;
    private final NudgeTiming timing;
    private final OutboxService outbox;
    private final CoachProperties.Nudge config;
    private final Clock clock;

    public NudgeWriter(GoalRepository goals, MilestoneRepository milestones, TaskRepository tasks, UserRepository users,
                       TaskSelector selector, NudgeTiming timing, OutboxService outbox, CoachProperties properties, Clock clock) {
        this.goals = goals;
        this.milestones = milestones;
        this.tasks = tasks;
        this.users = users;
        this.selector = selector;
        this.timing = timing;
        this.outbox = outbox;
        this.config = properties.nudge();
        this.clock = clock;
    }

    /**
     * Picks the next task of a due goal and queues its push: task QUEUED → SCHEDULED, SEND_NUDGE in the outbox,
     * next slot computed, all in one transaction. Two scheduler instances racing on the same goal: the goal's
     * {@code @Version} lets only one commit.
     *
     * @return true when a nudge was scheduled
     */
    @Transactional
    public boolean scheduleDueNudge(String goalId) {
        Instant now = clock.instant();
        Goal goal = goals.findById(goalId).orElse(null);
        if (goal == null || !goal.isActive() || goal.getNextNudgeAt() == null || goal.getNextNudgeAt().isAfter(now)) {
            return false;
        }
        User user = users.findById(goal.getUserId()).orElseThrow();
        Optional<Milestone> active = milestones.findFirstByGoalIdAndStatus(goalId, MilestoneStatus.ACTIVE);
        if (active.isEmpty()) {
            goal.scheduleNextNudge(null, now);
            return false;
        }
        Optional<CoachTask> next = selector.next(active.get().getId(), goal.getCurrentDifficulty(), now);
        if (next.isEmpty()) {
            // Pool is empty (or everything is snoozed): ask the AI for more and look again shortly.
            outbox.record(OutboxEventType.TOP_UP_TASKS, goalId, new TopUpCommand(goalId, "pool empty"));
            goal.scheduleNextNudge(now.plus(Duration.ofMinutes(config.emptyPoolRetryMinutes())), now);
            return false;
        }
        CoachTask task = next.get();
        task.schedule(now);
        outbox.record(OutboxEventType.SEND_NUDGE, goalId, new TaskCommand(goalId, task.getId()));
        goal.scheduleNextNudge(timing.next(goal, user, now), now);

        long remaining = tasks.countByMilestoneIdAndStatus(active.get().getId(), TaskStatus.QUEUED) - 1;
        if (remaining < config.minPoolSize()) {
            outbox.record(OutboxEventType.TOP_UP_TASKS, goalId, new TopUpCommand(goalId, "pool low"));
        }
        return true;
    }

    /** SCHEDULED → SENT after the push went out. Idempotent: a duplicate delivery finds the task already SENT. */
    @Transactional
    public boolean markSent(String taskId, boolean pushed) {
        CoachTask task = tasks.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != TaskStatus.SCHEDULED) return false;
        task.markSent(pushed, clock.instant());
        return true;
    }

    /** Recovery: a SCHEDULED task whose push never happened goes back to the pool and the goal is due again. */
    @Transactional
    public boolean requeueStuck(String taskId) {
        Instant now = clock.instant();
        CoachTask task = tasks.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != TaskStatus.SCHEDULED) return false;
        task.requeue(now);
        goals.findById(task.getGoalId()).ifPresent(g -> g.scheduleNextNudge(now, now));
        return true;
    }
}
