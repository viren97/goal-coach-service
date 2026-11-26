package dev.viren.coach.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.viren.coach.ai.AiModels.GeneratedMilestone;
import dev.viren.coach.ai.AiModels.GeneratedPlan;
import dev.viren.coach.ai.AiModels.GeneratedRecap;
import dev.viren.coach.ai.AiModels.GeneratedTask;
import dev.viren.coach.task.TaskKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiOutputValidatorTest {
    private final AiOutputValidator validator = new AiOutputValidator();

    @Test
    void clampsNumbersCapsTextAndDropsUnsafeLinks() {
        GeneratedTask wild = new GeneratedTask(TaskKind.LEARN, "x".repeat(500), "body", "http://insecure.example", 9, 500);
        GeneratedTask ok = new GeneratedTask(TaskKind.DO, "Practise", "b", "https://example.com/a", 0, 0);
        GeneratedPlan plan = validator.validate(new GeneratedPlan("s", List.of(new GeneratedMilestone("M1", "d", List.of(wild, ok)))));

        GeneratedTask first = plan.milestones().get(0).tasks().get(0);
        assertThat(first.title()).hasSize(200);
        assertThat(first.difficulty()).isEqualTo(5);
        assertThat(first.estMinutes()).isEqualTo(60);
        assertThat(first.resourceUrl()).isNull();
        GeneratedTask second = plan.milestones().get(0).tasks().get(1);
        assertThat(second.difficulty()).isEqualTo(1);
        assertThat(second.resourceUrl()).isEqualTo("https://example.com/a");
    }

    @Test
    void dropsBlankItemsAndRejectsEmptyPlans() {
        GeneratedTask blank = new GeneratedTask(TaskKind.DO, "  ", "b", "", 2, 10);
        assertThatThrownBy(() -> validator.validate(new GeneratedPlan("s", List.of(new GeneratedMilestone("M", "d", List.of(blank))))))
                .isInstanceOf(AiException.class);
        assertThatThrownBy(() -> validator.validate(new GeneratedPlan("s", List.of()))).isInstanceOf(AiException.class);
    }

    @Test
    void recapDifficultyChangeIsBounded() {
        GeneratedRecap recap = validator.validate(new GeneratedRecap("Nice week", List.of("a", "", "b", "c", "d"), 4));
        assertThat(recap.difficultyChange()).isEqualTo(1);
        assertThat(recap.highlights()).containsExactly("a", "b", "c");
    }
}
