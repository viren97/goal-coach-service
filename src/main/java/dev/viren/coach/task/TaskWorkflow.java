package dev.viren.coach.task;

import dev.viren.coach.command.Commands.TopUpCommand;
import dev.viren.coach.common.error.CoachException;
import dev.viren.coach.config.CoachProperties;
import dev.viren.coach.event.CoachEvent;
import dev.viren.coach.event.CoachEventPublisher;
import dev.viren.coach.event.CoachEventType;
import dev.viren.coach.goal.Goal;
import dev.viren.coach.goal.GoalRepository;
import dev.viren.coach.goal.Milestone;
import dev.viren.coach.goal.MilestoneRepository;
import dev.viren.coach.nudge.FatigueGuard;
import dev.viren.coach.nudge.NudgeTiming;
import dev.viren.coach.outbox.OutboxEventType;
import dev.viren.coach.outbox.OutboxService;
import dev.viren.coach.user.User;
import dev.viren.coach.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change to a task's status after it is created, with its consequences, in one DB transaction:
 * difficulty adaptation, milestone/goal progression, fatigue, and the outbox rows for events and follow-up commands.
 */
@Component
public class TaskWorkflow {
    private final TaskRepository tasks;
    private final GoalRepository goals;
    private final MilestoneRepository milestones;
    private final UserRepository users;
    private final DifficultyPolicy difficulty;
    private final FatigueGuard fatigue;
    private final NudgeTiming timing;
    private final OutboxService outbox;
    private final CoachEventPublisher events;
    private final CoachProperties.Nudge config;
    private final Clock clock;

    public TaskWorkflow(TaskRepository tasks, GoalRepository goals, MilestoneRepository milestones, UserRepository users,
                        DifficultyPolicy difficulty, FatigueGuard fatigue, NudgeTiming timing, OutboxService outbox,
                        CoachEventPublisher events, CoachProperties properties, Clock clock) {
        this.tasks = tasks;
        this.goals = goals;
        this.milestones = milestones;
        this.users = users;
        this.difficulty = difficulty;
        this.fatigue = fatigue;
        this.timing = timing;
        this.outbox = outbox;
        this.events = events;
        this.config = properties.nudge();
        this.clock = clock;
    }

    public record ActionResult(CoachTask task, boolean replayed) {}

    @Transactional
    public ActionResult act(String taskId, TaskAction action, String note, String proofUrl, Long snoozeMinutes) {
        Instant now = clock.instant();
        CoachTask task = tasks.findById(taskId).orElseThrow(() -> CoachException.notFound("Task", taskId));

        // Replays (double tap, notification button + app) return the current state instead of failing.
        if (action == TaskAction.DONE && task.getStatus() == TaskStatus.DONE) return new ActionResult(task, true);
        if ((action == TaskAction.SWAP || action == TaskAction.TOO_HARD) && task.getStatus() == TaskStatus.SWAPPED) {
            return new ActionResult(task, true);
        }

        Goal goal = goals.findById(task.getGoalId()).orElseThrow();
        switch (action) {
            case DONE -> {
                task.complete(note, proofUrl, now);
                goal.applyDifficulty(difficulty.onDone(goal.difficultyState()), now);
                publish(CoachEventType.TASK_COMPLETED, goal, task, task.getTitle(), null, now);
                progressMilestone(goal, task.getMilestoneId(), now);
            }
            case SNOOZE -> {
                long minutes = snoozeMinutes != null ? snoozeMinutes : config.defaultSnoozeMinutes();
                task.snooze(now.plus(Duration.ofMinutes(minutes)), now);
                goal.applyDifficulty(difficulty.onEngaged(goal.difficultyState()), now);
                publish(CoachEventType.TASK_SNOOZED, goal, task, task.getTitle(), null, now);
            }
            case SWAP, TOO_HARD -> {
                task.swap(now);
                DifficultyState state = goal.difficultyState();
                goal.applyDifficulty(action == TaskAction.TOO_HARD ? difficulty.onTooHard(state) : difficulty.onEngaged(state), now);
                // Replace it now: the goal is due immediately, and the pool gets topped up in the background.
                goal.scheduleNextNudge(now, now);
                outbox.record(OutboxEventType.TOP_UP_TASKS, goal.getId(), new TopUpCommand(goal.getId(), action.name().toLowerCase()));
                publish(CoachEventType.TASK_SWAPPED, goal, task, task.getTitle(), action.name(), now);
            }
        }
        return new ActionResult(task, false);
    }

    /** A pushed task nobody acted on. Counts towards fatigue only if a push actually reached a device. */
    @Transactional
    public boolean expire(String taskId) {
        Instant now = clock.instant();
        CoachTask task = tasks.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != TaskStatus.SENT) return false;
        task.expire(now);
        Goal goal = goals.findById(task.getGoalId()).orElseThrow();
        publish(CoachEventType.TASK_EXPIRED, goal, task, task.getTitle(), null, now);
        if (!task.isPushed()) return true;

        goal.applyDifficulty(difficulty.onIgnored(goal.difficultyState()), now);
        if (fatigue.shouldCheckIn(goal.getIgnoredStreak()) && goal.isActive()) {
            User user = users.findById(goal.getUserId()).orElseThrow();
            // Back off: from now on one nudge a day until the user acts again.
            goal.scheduleNextNudge(timing.next(goal, user, now.plus(Duration.ofHours(12))), now);
            publish(CoachEventType.FATIGUE_CHECK_IN, goal, null, "Want to make this easier?",
                    "You've had a busy few days. Should we make the tasks smaller, keep them as they are, or pause?", now);
        }
        return true;
    }

    private void progressMilestone(Goal goal, String milestoneId, Instant now) {
        Milestone milestone = milestones.findById(milestoneId).orElseThrow();
        long done = tasks.countByMilestoneIdAndStatus(milestoneId, TaskStatus.DONE);
        if (done < milestone.getTargetDone() || milestone.getStatus() != dev.viren.coach.goal.MilestoneStatus.ACTIVE) return;

        milestone.complete(now);
        publish(CoachEventType.MILESTONE_COMPLETED, goal, null, milestone.getTitle(), milestone.getDescription(), now);
        milestones.findByGoalIdAndPosition(goal.getId(), milestone.getPosition() + 1).ifPresentOrElse(
                next -> {
                    next.activate();
                    goal.scheduleNextNudge(now, now); // first task of the new milestone right away
                },
                () -> {
                    if (goal.isActive()) {
                        goal.complete(now);
                        publish(CoachEventType.GOAL_COMPLETED, goal, null, goal.getIdentity(), null, now);
                    }
                });
    }

    private void publish(CoachEventType type, Goal goal, CoachTask task, String title, String message, Instant now) {
        events.publish(new CoachEvent(type, goal.getId(), goal.getUserId(), task == null ? null : task.getId(), title, message, now));
    }
}
