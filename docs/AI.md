# AI integration

The AI does three jobs, behind one port (`ai/AiCoach`):

| Call | When | Model (default) | Output record |
|---|---|---|---|
| `generatePlan` | Once per goal, at onboarding | `claude-opus-5-5` (`coach.ai.plan-model`) | `GeneratedPlan`: summary, 3–5 milestones × 4–6 tasks |
| `generateTasks` | When a milestone's pool runs low, or after a swap | `claude-haiku-5-5` (`coach.ai.quick-model`) | `GeneratedTaskBatch` |
| `weeklyRecap` | Sunday 18:00 local | `claude-opus-5-5` | `GeneratedRecap`: message, highlights, difficulty −1/0/+1 |

Both model ids are configuration. To trade quality for cost, set `coach.ai.plan-model` to `claude-sonnet-5-5`.

## How a call works

`ai/ClaudeAiCoach` uses the official **Anthropic Java SDK** with **structured outputs**:

```java
StructuredMessageCreateParams<GeneratedPlan> params = MessageCreateParams.builder()
        .model(config.planModel())
        .maxTokens(config.maxTokens())
        .system(SYSTEM)
        .outputConfig(GeneratedPlan.class)   // JSON schema derived from the Java record
        .addUserMessage(prompt)
        .build();
StructuredMessage<GeneratedPlan> message = client.messages().create(params);
```

- The response is constrained to the record's JSON schema, so the code never parses free text.
  `@JsonPropertyDescription` annotations on the records (`AiModels`) tell the model what each field means.
- The `stop_reason` is checked: `refusal` and `max_tokens` raise `AiException`.
- The system prompt holds the rules: 5–30 minute concrete tasks, mostly DO, https links only when certain,
  no medical/legal/financial advice. User text (identity, context) is placed in the user turn inside tags,
  as data.
- The SDK retries 429/5xx and connection errors (2 retries) with a 120 s timeout (`coach.ai.timeout-seconds`).

## Guard rails

`ai/GuardedAiCoach` is the bean every caller gets (`@Primary`):

1. Calls Claude when `coach.ai.provider=claude`, otherwise the template coach.
2. Runs the result through `AiOutputValidator`, which treats model output as untrusted input: it caps every
   string length, clamps difficulty to 1–5 and minutes to 1–60, keeps only `https://` links, drops blank items,
   limits counts (≤ 6 milestones, ≤ 10 tasks each), and rejects plans with nothing usable.
3. On **any** failure (network, timeout, refusal, truncation, invalid output) it logs a warning and returns the
   validated `TemplateAiCoach` result instead.

The AI is never on the request path: every call happens in a queue worker, outside a DB transaction, and its
result is applied in a separate short transaction that re-checks state (for example, a plan is applied only while
the goal is still `PLANNING`).

## Cost control

- **Batching:** one call produces the whole plan (often 15–25 tasks). Nudges don't call the AI.
- **Top-ups are small:** 5 tasks on the cheaper model, only when fewer than 3 are queued.
- **Duplicate commands are cheap:** a re-delivered `GENERATE_PLAN` or `TOP_UP_TASKS` returns before calling the
  model (the goal is no longer `PLANNING`, or the pool is already full).
- **Recaps:** at most one per goal per week (claimed in the DB, unique per week).
- Not yet built: a per-user daily token budget and prompt caching of the system prompt ([roadmap](ROADMAP.md)).

## Running with Claude

```bash
COACH_AI_PROVIDER=claude ANTHROPIC_API_KEY=sk-ant-... ./gradlew bootRun
```
At startup the log shows `AI coach: ClaudeAiCoach`. Without a key, startup fails fast when the provider is `claude`.

## Privacy

Only the goal's identity, the optional context, the milestone being worked on, recent task titles and weekly
counts are sent to the model. Emails, names, push tokens and free-text task notes are never sent.
