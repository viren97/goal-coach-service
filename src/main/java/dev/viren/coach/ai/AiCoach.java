package dev.viren.coach.ai;

import dev.viren.coach.ai.AiModels.GeneratedPlan;
import dev.viren.coach.ai.AiModels.GeneratedRecap;
import dev.viren.coach.ai.AiModels.GeneratedTaskBatch;
import dev.viren.coach.ai.AiModels.PlanRequest;
import dev.viren.coach.ai.AiModels.RecapRequest;
import dev.viren.coach.ai.AiModels.TaskBatchRequest;

/**
 * Port for everything the AI does. Adapters: {@link TemplateAiCoach} (deterministic, no key, default) and
 * {@link ClaudeAiCoach}. Callers get {@link GuardedAiCoach}, which validates output and falls back to the template.
 * Never called inside a DB transaction: these calls are slow and may fail.
 */
public interface AiCoach {
    GeneratedPlan generatePlan(PlanRequest request);

    GeneratedTaskBatch generateTasks(TaskBatchRequest request);

    GeneratedRecap weeklyRecap(RecapRequest request);
}
