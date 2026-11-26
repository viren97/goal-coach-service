package dev.viren.coach.nudge;

import dev.viren.coach.task.CoachTask;
import dev.viren.coach.task.TaskRepository;
import dev.viren.coach.task.TaskStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Picks the next task from the active milestone: available now, closest to the current difficulty, earliest first. */
@Component
public class TaskSelector {
    private final TaskRepository tasks;

    public TaskSelector(TaskRepository tasks) {
        this.tasks = tasks;
    }

    public Optional<CoachTask> next(String milestoneId, int targetDifficulty, Instant now) {
        return pick(tasks.findByMilestoneIdAndStatus(milestoneId, TaskStatus.QUEUED), targetDifficulty, now);
    }

    static Optional<CoachTask> pick(List<CoachTask> pool, int targetDifficulty, Instant now) {
        return pool.stream()
                .filter(t -> t.isAvailableAt(now))
                .min(Comparator.<CoachTask>comparingInt(t -> Math.abs(t.getDifficulty() - targetDifficulty))
                        .thenComparingInt(CoachTask::getPosition));
    }
}
