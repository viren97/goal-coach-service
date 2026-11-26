package dev.viren.coach.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.viren.coach.ai.AiModels.GeneratedMilestone;
import dev.viren.coach.ai.AiModels.GeneratedPlan;
import dev.viren.coach.ai.AiModels.GeneratedRecap;
import dev.viren.coach.ai.AiModels.GeneratedTaskBatch;
import dev.viren.coach.ai.AiModels.PlanRequest;
import dev.viren.coach.ai.AiModels.RecapRequest;
import dev.viren.coach.ai.AiModels.TaskBatchRequest;
import dev.viren.coach.config.CoachProperties;
import dev.viren.coach.support.TestProperties;
import dev.viren.coach.user.Intensity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class GuardedAiCoachTest {

    /** A Claude adapter (fake key, never called over the network) whose answers the test controls. */
    static class ScriptedClaude extends ClaudeAiCoach {
        RuntimeException failure;
        GeneratedPlan plan;

        ScriptedClaude() {
            super(withKey(TestProperties.defaults()));
        }

        @Override public GeneratedPlan generatePlan(PlanRequest r) {
            if (failure != null) throw failure;
            return plan;
        }
        @Override public GeneratedTaskBatch generateTasks(TaskBatchRequest r) { throw new AiException("down"); }
        @Override public GeneratedRecap weeklyRecap(RecapRequest r) { throw new AiException("down"); }

        private static CoachProperties withKey(CoachProperties p) {
            CoachProperties.Ai ai = new CoachProperties.Ai("claude", p.ai().planModel(), p.ai().quickModel(),
                    p.ai().maxTokens(), p.ai().timeoutSeconds(), "test-key-not-used");
            return new CoachProperties(p.goals(), p.queue(), p.outbox(), p.nudge(), p.difficulty(), p.recap(), p.sweeper(), ai, p.push());
        }
    }

    private final PlanRequest request = new PlanRequest("a runner", null, Intensity.STEADY, 2);

    private GuardedAiCoach guarded(ScriptedClaude claude) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("claude", claude);
        return new GuardedAiCoach(beans.getBeanProvider(ClaudeAiCoach.class), new TemplateAiCoach(), new AiOutputValidator());
    }

    @Test
    void usesTheModelWhenItsOutputIsValid() {
        ScriptedClaude claude = new ScriptedClaude();
        claude.plan = new GeneratedPlan("model plan", List.of(new GeneratedMilestone("Run 5k", "d",
                List.of(new AiModels.GeneratedTask(dev.viren.coach.task.TaskKind.DO, "Jog 10 minutes", "b", "", 2, 10)))));
        assertThat(guarded(claude).generatePlan(request).summary()).isEqualTo("model plan");
    }

    @Test
    void fallsBackToTemplateWhenTheModelFails() {
        ScriptedClaude claude = new ScriptedClaude();
        claude.failure = new AiException("Model declined the request");
        GeneratedPlan plan = guarded(claude).generatePlan(request);
        assertThat(plan.milestones()).hasSize(3);
        assertThat(plan.milestones().get(0).tasks()).hasSize(5);
    }

    @Test
    void fallsBackWhenTheModelReturnsAnUnusablePlan() {
        ScriptedClaude claude = new ScriptedClaude();
        claude.plan = new GeneratedPlan("empty", List.of());
        assertThat(guarded(claude).generatePlan(request).milestones()).isNotEmpty();
    }

    @Test
    void topUpsAndRecapsFallBackToo() {
        GuardedAiCoach coach = guarded(new ScriptedClaude());
        assertThat(coach.generateTasks(new TaskBatchRequest("a runner", "M", "d", 2, 4, List.of(), "pool low")).tasks()).hasSize(4);
        assertThat(coach.weeklyRecap(new RecapRequest("a runner", 6, 0, 0, 0, 2, 3, List.of("Jog"))).difficultyChange()).isEqualTo(1);
    }
}
