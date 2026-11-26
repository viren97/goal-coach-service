package dev.viren.coach.progress;

import dev.viren.coach.goal.GoalStatus;
import dev.viren.coach.goal.MilestoneStatus;
import java.time.Instant;
import java.util.List;

/**
 * @param identityScore  sum of the difficulty of every task done: grows faster as tasks get harder
 * @param pushActionRate share of pushes in the last 7 days that led to an action (done, snooze, swap); null with no pushes
 */
public record ProgressView(
        String goalId,
        String identity,
        GoalStatus status,
        int tasksDone,
        int identityScore,
        int currentStreakDays,
        int longestStreakDays,
        int currentDifficulty,
        Double pushActionRate,
        List<MilestoneProgress> milestones,
        Instant computedAt) {

    public record MilestoneProgress(String id, String title, MilestoneStatus status, int done, int target) {}
}
