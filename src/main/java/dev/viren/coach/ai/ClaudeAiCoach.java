package dev.viren.coach.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import dev.viren.coach.ai.AiModels.GeneratedPlan;
import dev.viren.coach.ai.AiModels.GeneratedRecap;
import dev.viren.coach.ai.AiModels.GeneratedTaskBatch;
import dev.viren.coach.ai.AiModels.PlanRequest;
import dev.viren.coach.ai.AiModels.RecapRequest;
import dev.viren.coach.ai.AiModels.TaskBatchRequest;
import dev.viren.coach.config.CoachProperties;
import java.time.Duration;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link AiCoach} on Claude via the official Anthropic Java SDK, using structured outputs: the response is
 * constrained to the JSON schema derived from the {@code AiModels} records, so there is no free-text parsing.
 *
 * <ul>
 *   <li>Plans and recaps use {@code coach.ai.plan-model}; task top-ups and swaps use the cheaper {@code quick-model}.</li>
 *   <li>A plan is generated once per goal, with every milestone's tasks in one call (batching is the main cost control).</li>
 *   <li>Refusals and truncated output throw {@link AiException}; {@link GuardedAiCoach} then falls back to the template.</li>
 *   <li>User text (identity, context) goes in the user turn as data, never into the system prompt.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "coach.ai", name = "provider", havingValue = "claude")
public class ClaudeAiCoach implements AiCoach, DisposableBean {

    static final String SYSTEM = """
            You are a pragmatic personal coach inside a mobile app. The user tells you who they want to become.
            You turn that identity into a plan of milestones, each made of small tasks the app sends as push notifications.

            Rules for every task:
            - One concrete action that fits in 5-30 minutes and can be finished today.
            - LEARN tasks: read or watch something short. DO tasks: act, practise, produce something.
            - Mix LEARN and DO; most tasks should be DO.
            - Only include a resourceUrl when you are confident the https URL exists and is stable; otherwise use "".
            - Difficulty 1 is tiny (2 minutes of effort), 5 is a stretch. Respect the requested difficulty.
            - No medical, legal or financial advice. Nothing unsafe, nothing that costs money.
            - Write in plain, warm, second-person English. No emojis.
            """;

    private final AnthropicClient client;
    private final CoachProperties.Ai config;

    public ClaudeAiCoach(CoachProperties properties) {
        this.config = properties.ai();
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException("coach.ai.provider=claude needs ANTHROPIC_API_KEY");
        }
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(config.apiKey())
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .maxRetries(2)
                .build();
    }

    @Override
    public GeneratedPlan generatePlan(PlanRequest r) {
        String prompt = """
                Create a coaching plan.
                Identity the user wants: <identity>%s</identity>
                Extra context from the user: <context>%s</context>
                Pace: %s (%d nudges per day). Start at difficulty %d and ramp up gradually across milestones.
                Return 3 to 5 milestones with 4 to 6 tasks each.
                """.formatted(r.identity(), r.context() == null ? "" : r.context(), r.intensity(),
                r.intensity().pushesPerDay(), r.startDifficulty());
        return call(config.planModel(), GeneratedPlan.class, prompt);
    }

    @Override
    public GeneratedTaskBatch generateTasks(TaskBatchRequest r) {
        String prompt = """
                Write %d new tasks for the current milestone.
                Identity: <identity>%s</identity>
                Milestone: <milestone>%s: %s</milestone>
                Target difficulty: %d. Why new tasks are needed: %s.
                Do not repeat or closely paraphrase these recent tasks:
                <recent>%s</recent>
                """.formatted(r.count(), r.identity(), r.milestoneTitle(),
                r.milestoneDescription() == null ? "" : r.milestoneDescription(),
                r.difficulty(), r.reason(), String.join("\n", r.recentTitles()));
        return call(config.quickModel(), GeneratedTaskBatch.class, prompt);
    }

    @Override
    public GeneratedRecap weeklyRecap(RecapRequest r) {
        String prompt = """
                Write the user's weekly recap.
                Identity: <identity>%s</identity>
                This week: %d done, %d snoozed, %d swapped, %d ignored. Current streak: %d days. Difficulty: %d of 5.
                Tasks completed: <done>%s</done>
                Suggest difficultyChange: +1 if they finished most tasks easily, -1 if they ignored or swapped most, else 0.
                """.formatted(r.identity(), r.done(), r.snoozed(), r.swapped(), r.ignored(), r.streakDays(),
                r.currentDifficulty(), String.join("\n", r.doneTitles()));
        return call(config.planModel(), GeneratedRecap.class, prompt);
    }

    private <T> T call(String model, Class<T> outputType, String userPrompt) {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(config.maxTokens())
                .system(SYSTEM)
                .outputConfig(outputType)
                .addUserMessage(userPrompt)
                .build();

        StructuredMessage<T> message = client.messages().create(params);

        StopReason stop = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) throw new AiException("Model declined the request");
        if (StopReason.MAX_TOKENS.equals(stop)) throw new AiException("Model output was truncated (max_tokens)");

        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new AiException("Model returned no structured output"));
    }

    @Override
    public void destroy() {
        client.close();
    }
}
