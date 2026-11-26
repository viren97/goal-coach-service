# Architecture decision records

Short records of the decisions that shape this service: the context, the options considered, what was chosen,
and what it costs.

| ADR | Decision | Status |
|---|---|---|
| [001](001-transactional-outbox.md) | Transactional outbox for every command and event | Accepted |
| [002](002-indexed-next-nudge-polling.md) | Schedule nudges by polling an indexed `next_nudge_at` column | Accepted |
| [003](003-optimistic-locking.md) | Optimistic locking with whole-transaction retry instead of row locks | Accepted |
| [004](004-ai-behind-a-guarded-port.md) | Keep the AI behind a validated port with a deterministic fallback | Accepted |
