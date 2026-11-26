package dev.viren.coach.ai;

import dev.viren.coach.ai.AiModels.GeneratedPlan;
import dev.viren.coach.ai.AiModels.GeneratedRecap;
import dev.viren.coach.ai.AiModels.GeneratedTaskBatch;
import dev.viren.coach.ai.AiModels.PlanRequest;
import dev.viren.coach.ai.AiModels.RecapRequest;
import dev.viren.coach.ai.AiModels.TaskBatchRequest;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The {@link AiCoach} every caller gets. Uses Claude when it is configured, validates whatever comes back, and
 * falls back to {@link TemplateAiCoach} on any failure (timeout, refusal, invalid JSON, empty plan). A user never
 * waits on, or loses a plan to, an AI outage.
 */
@Primary
@Component
public class GuardedAiCoach implements AiCoach {
    private static final Logger log = LoggerFactory.getLogger(GuardedAiCoach.class);

    private final AiCoach primary;
    private final TemplateAiCoach fallback;
    private final AiOutputValidator validator;

    public GuardedAiCoach(ObjectProvider<ClaudeAiCoach> claude, TemplateAiCoach fallback, AiOutputValidator validator) {
        ClaudeAiCoach configured = claude.getIfAvailable();
        this.primary = configured != null ? configured : fallback;
        this.fallback = fallback;
        this.validator = validator;
        log.info("AI coach: {}", primary.getClass().getSimpleName());
    }

    @Override
    public GeneratedPlan generatePlan(PlanRequest request) {
        return guarded("plan", () -> validator.validate(primary.generatePlan(request)),
                () -> validator.validate(fallback.generatePlan(request)));
    }

    @Override
    public GeneratedTaskBatch generateTasks(TaskBatchRequest request) {
        int max = Math.max(1, request.count());
        return guarded("tasks", () -> validator.validate(primary.generateTasks(request), max),
                () -> validator.validate(fallback.generateTasks(request), max));
    }

    @Override
    public GeneratedRecap weeklyRecap(RecapRequest request) {
        return guarded("recap", () -> validator.validate(primary.weeklyRecap(request)),
                () -> validator.validate(fallback.weeklyRecap(request)));
    }

    private <T> T guarded(String what, Supplier<T> call, Supplier<T> onFailure) {
        if (primary == fallback) return onFailure.get();
        try {
            return call.get();
        } catch (Exception e) {
            log.warn("AI {} generation failed, using template fallback: {}", what, e.toString());
            return onFailure.get();
        }
    }
}
