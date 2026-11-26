# ADR-004: Keep the AI behind a validated port with a deterministic fallback

**Status:** Accepted

## Context

Plans, task top-ups and recaps come from an LLM. LLM calls are slow (seconds), cost money, can time out or be
rate-limited, can refuse, and can return output that is valid JSON but wrong for the product: an empty plan,
a 3-hour task, a broken link. Onboarding must not fail because of any of that.

## Options

| Option | Problem |
|---|---|
| Call the model synchronously in `POST /goals` | The request takes seconds; an outage becomes user-facing errors |
| Free-text output + parsing | Fragile parsing; formats drift |
| Trust the model's output | Unsafe links, absurd numbers and empty plans reach users |
| **Async call → structured output → validation → fallback** | More moving parts; fallback plans are generic |

## Decision

- `AiCoach` is a port. `ClaudeAiCoach` uses the Anthropic Java SDK with **structured outputs**, so the response is
  constrained to a JSON schema derived from Java records (`AiModels`). `TemplateAiCoach` is deterministic.
- `GuardedAiCoach` (`@Primary`) wraps the configured adapter. `AiOutputValidator` caps lengths, clamps difficulty
  and duration, keeps only https links, and rejects structurally empty results. **Any** failure (network,
  timeout, refusal, `max_tokens`, invalid output) falls back to the validated template.
- Calls run only in queue workers and never inside a DB transaction. Results are applied in a short transaction
  that re-checks state.
- Plans use the stronger model once per goal. Frequent small top-ups use the cheaper one. Both are configuration.

## Consequences

- ✅ An AI outage degrades quality, never availability. Users always get a plan.
- ✅ The rest of the system and the tests run with no API key (`provider=template`).
- ✅ Cost is bounded by design: one plan call per goal, small top-ups only when the pool is low, one recap a week.
- ⚠️ Fallback plans are generic; the fallback rate should be monitored (roadmap: metrics).
