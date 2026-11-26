package dev.viren.coach.ai;

import dev.viren.coach.ai.AiModels.GeneratedMilestone;
import dev.viren.coach.ai.AiModels.GeneratedPlan;
import dev.viren.coach.ai.AiModels.GeneratedRecap;
import dev.viren.coach.ai.AiModels.GeneratedTask;
import dev.viren.coach.ai.AiModels.GeneratedTaskBatch;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Model output is untrusted input. This turns it into something safe to store and push: lengths capped, numbers
 * clamped, only https links kept, blank items dropped. Structural problems (no milestones, no tasks) throw, so
 * {@link GuardedAiCoach} falls back to the template instead of saving a broken plan.
 */
@Component
public class AiOutputValidator {
    static final int MAX_MILESTONES = 6;
    static final int MAX_TASKS_PER_MILESTONE = 10;

    public GeneratedPlan validate(GeneratedPlan plan) {
        if (plan == null || plan.milestones() == null || plan.milestones().isEmpty()) {
            throw new AiException("Plan has no milestones");
        }
        List<GeneratedMilestone> milestones = plan.milestones().stream()
                .filter(Objects::nonNull)
                .filter(m -> !isBlank(m.title()))
                .limit(MAX_MILESTONES)
                .map(m -> new GeneratedMilestone(cap(m.title(), 200), cap(m.description(), 1000),
                        tasks(m.tasks(), MAX_TASKS_PER_MILESTONE)))
                .filter(m -> !m.tasks().isEmpty())
                .toList();
        if (milestones.isEmpty()) throw new AiException("Plan has no usable milestones");
        return new GeneratedPlan(cap(plan.summary(), 2000), milestones);
    }

    public GeneratedTaskBatch validate(GeneratedTaskBatch batch, int maxTasks) {
        if (batch == null) throw new AiException("Empty task batch");
        List<GeneratedTask> tasks = tasks(batch.tasks(), maxTasks);
        if (tasks.isEmpty()) throw new AiException("Task batch has no usable tasks");
        return new GeneratedTaskBatch(tasks);
    }

    public GeneratedRecap validate(GeneratedRecap recap) {
        if (recap == null || isBlank(recap.message())) throw new AiException("Recap has no message");
        List<String> highlights = recap.highlights() == null ? List.of()
                : recap.highlights().stream().filter(h -> !isBlank(h)).limit(3).map(h -> cap(h, 200)).toList();
        return new GeneratedRecap(cap(recap.message(), 500), highlights,
                Math.max(-1, Math.min(1, recap.difficultyChange())));
    }

    private List<GeneratedTask> tasks(List<GeneratedTask> tasks, int max) {
        if (tasks == null) return List.of();
        return tasks.stream()
                .filter(Objects::nonNull)
                .filter(t -> t.kind() != null && !isBlank(t.title()))
                .limit(max)
                .map(t -> new GeneratedTask(t.kind(), cap(t.title(), 200), cap(t.body(), 1500), safeUrl(t.resourceUrl()),
                        Math.max(1, Math.min(5, t.difficulty())), Math.max(1, Math.min(60, t.estMinutes()))))
                .toList();
    }

    static String safeUrl(String url) {
        if (isBlank(url)) return null;
        String trimmed = url.trim();
        return trimmed.startsWith("https://") && trimmed.length() <= 1000 && !trimmed.contains(" ") ? trimmed : null;
    }

    private static String cap(String value, int max) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
