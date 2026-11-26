package dev.viren.coach.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.viren.coach.task.TaskKind;
import dev.viren.coach.user.Intensity;
import java.util.List;

/**
 * Inputs and outputs of the {@link AiCoach} port. The output records double as the JSON schema for Claude's
 * structured outputs, so the descriptions here are part of the prompt.
 */
public final class AiModels {
    private AiModels() {}

    // ---------- inputs ----------

    public record PlanRequest(String identity, String context, Intensity intensity, int startDifficulty) {}

    public record TaskBatchRequest(
            String identity,
            String milestoneTitle,
            String milestoneDescription,
            int difficulty,
            int count,
            List<String> recentTitles,
            String reason) {}

    public record RecapRequest(
            String identity,
            int done,
            int snoozed,
            int swapped,
            int ignored,
            int currentDifficulty,
            int streakDays,
            List<String> doneTitles) {}

    // ---------- outputs ----------

    public record GeneratedTask(
            @JsonPropertyDescription("LEARN for reading/watching something short, DO for a concrete action")
            TaskKind kind,
            @JsonPropertyDescription("Imperative, specific, under 80 characters, e.g. 'Record a 60-second video explaining your day'")
            String title,
            @JsonPropertyDescription("One or two sentences: exactly what to do and what done looks like")
            String body,
            @JsonPropertyDescription("An https URL for LEARN tasks only when you are confident it exists; otherwise an empty string")
            String resourceUrl,
            @JsonPropertyDescription("1 (tiny) to 5 (stretch)")
            int difficulty,
            @JsonPropertyDescription("Estimated minutes, 5 to 30")
            int estMinutes) {}

    public record GeneratedMilestone(
            @JsonPropertyDescription("Short milestone name, under 60 characters")
            String title,
            @JsonPropertyDescription("What reaching this milestone means, one sentence")
            String description,
            @JsonPropertyDescription("4 to 6 tasks, easiest first")
            List<GeneratedTask> tasks) {}

    public record GeneratedPlan(
            @JsonPropertyDescription("Two sentences on how this plan gets the user to the identity")
            String summary,
            @JsonPropertyDescription("3 to 5 milestones, in order")
            List<GeneratedMilestone> milestones) {}

    public record GeneratedTaskBatch(List<GeneratedTask> tasks) {}

    public record GeneratedRecap(
            @JsonPropertyDescription("Encouraging, specific weekly message to the user, under 280 characters, second person")
            String message,
            @JsonPropertyDescription("Up to 3 short highlights from the week")
            List<String> highlights,
            @JsonPropertyDescription("-1 to make next week's tasks easier, 0 to keep, +1 to make them harder")
            int difficultyChange) {}
}
