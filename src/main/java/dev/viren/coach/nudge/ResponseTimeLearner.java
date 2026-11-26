package dev.viren.coach.nudge;

import dev.viren.coach.task.CoachTask;
import dev.viren.coach.task.TaskRepository;
import dev.viren.coach.task.TaskStatus;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Learns the local hour at which a user most often completes pushed tasks (needs a few samples first). */
@Component
public class ResponseTimeLearner {
    static final int MIN_SAMPLES = 5;

    private final TaskRepository tasks;

    public ResponseTimeLearner(TaskRepository tasks) {
        this.tasks = tasks;
    }

    public Integer preferredHour(String userId, ZoneId zone) {
        List<CoachTask> recent = tasks.findTop30ByUserIdAndStatusAndPushedTrueOrderByActedAtDesc(userId, TaskStatus.DONE);
        List<Integer> hours = recent.stream()
                .map(CoachTask::getActedAt)
                .filter(Objects::nonNull)
                .map(at -> at.atZone(zone).getHour())
                .toList();
        return mostCommon(hours);
    }

    static Integer mostCommon(List<Integer> hours) {
        if (hours.size() < MIN_SAMPLES) return null;
        Map<Integer, Long> counts = hours.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream()
                .max(Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
