package dev.viren.coach.progress;

import dev.viren.coach.common.error.CoachException;
import dev.viren.coach.goal.Goal;
import dev.viren.coach.goal.GoalRepository;
import dev.viren.coach.goal.Milestone;
import dev.viren.coach.goal.MilestoneRepository;
import dev.viren.coach.progress.ProgressView.MilestoneProgress;
import dev.viren.coach.task.CoachTask;
import dev.viren.coach.task.TaskRepository;
import dev.viren.coach.task.TaskStatus;
import dev.viren.coach.user.User;
import dev.viren.coach.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService {
    private final GoalRepository goals;
    private final MilestoneRepository milestones;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final ProgressCache cache;
    private final Clock clock;

    public ProgressService(GoalRepository goals, MilestoneRepository milestones, TaskRepository tasks,
                           UserRepository users, ProgressCache cache, Clock clock) {
        this.goals = goals;
        this.milestones = milestones;
        this.tasks = tasks;
        this.users = users;
        this.cache = cache;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProgressView get(String goalId) {
        return cache.get(goalId).orElseGet(() -> {
            ProgressView view = compute(goalId);
            cache.put(view);
            return view;
        });
    }

    private ProgressView compute(String goalId) {
        Goal goal = goals.findById(goalId).orElseThrow(() -> CoachException.notFound("Goal", goalId));
        User user = users.findById(goal.getUserId()).orElseThrow();
        ZoneId zone = user.zone();
        Instant now = clock.instant();

        List<CoachTask> all = tasks.findByGoalIdOrderByPositionAsc(goalId);
        List<CoachTask> done = all.stream().filter(t -> t.getStatus() == TaskStatus.DONE).toList();

        Map<String, Long> doneByMilestone = done.stream()
                .collect(Collectors.groupingBy(CoachTask::getMilestoneId, Collectors.counting()));
        List<MilestoneProgress> milestoneViews = milestones.findByGoalIdOrderByPositionAsc(goalId).stream()
                .map((Milestone m) -> new MilestoneProgress(m.getId(), m.getTitle(), m.getStatus(),
                        (int) Math.min(m.getTargetDone(), doneByMilestone.getOrDefault(m.getId(), 0L)), m.getTargetDone()))
                .toList();

        List<LocalDate> activeDays = done.stream()
                .filter(t -> t.getActedAt() != null)
                .map(t -> t.getActedAt().atZone(zone).toLocalDate())
                .toList();
        StreakCalculator.Streaks streaks = StreakCalculator.compute(activeDays, now.atZone(zone).toLocalDate());

        Instant weekAgo = now.minus(Duration.ofDays(7));
        List<CoachTask> pushedThisWeek = all.stream()
                .filter(t -> t.isPushed() && t.getSentAt() != null && t.getSentAt().isAfter(weekAgo))
                .toList();
        long acted = pushedThisWeek.stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE || t.getStatus() == TaskStatus.SWAPPED || t.getSnoozeCount() > 0)
                .count();
        Double rate = pushedThisWeek.isEmpty() ? null : (double) acted / pushedThisWeek.size();

        return new ProgressView(goalId, goal.getIdentity(), goal.getStatus(), done.size(),
                done.stream().mapToInt(CoachTask::getDifficulty).sum(), streaks.current(), streaks.longest(),
                goal.getCurrentDifficulty(), rate, milestoneViews, now);
    }
}
