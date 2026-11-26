# Roadmap

## Next

| Item | Why |
|---|---|
| **Auth** (Firebase / Supabase JWT via Spring Security resource server) | Today any caller can act for any user id |
| **Flyway migrations** | `ddl-auto: update` is fine for a demo, unsafe for production schema changes |
| **Metrics & tracing** (Micrometer + OpenTelemetry) | Push → action rate, AI fallback rate and latency, queue lag |
| **Per-user AI budget** | Hard cap on AI calls per user per day, stored in the DB so it holds across replicas |
| **Prompt caching** | The system prompt is identical on every call; cache it to cut input cost |
| **Proof of action** | Upload photos for `proofUrl` (pre-signed S3 URLs) and feed proof notes into the next tasks |

## Later

| Item | Why |
|---|---|
| Multiple goals per user | Needs fair slot sharing between goals in one send window |
| Smarter timing | A per-weekday learned hour, and quiet days (no nudges on chosen days) |
| Plan regeneration | "Rethink my plan" after a few weeks, keeping completed milestones |
| Reflection prompts | A one-line "How did that feel?" after DONE, used as context for next tasks |
| Accountability partners | Share progress with a friend; partner nudges |
| Integrations | Calendar (avoid busy slots), Strava/GitHub (auto-complete tasks from activity) |
| Sharded scheduler | Partition due-goal scans by hash for very large user counts |
| Web push / email channels | More `PushGateway` adapters |

## Known limitations

- Duplicate top-up commands that run truly concurrently can both add a batch (the pool ends up slightly larger than needed).
- The recap scheduler scans active goals hourly; past roughly 1M active goals it should use a `next_recap_at` column like nudges do.
- SQLite (default profile) serialises writes on one connection; use the `docker` profile for realistic concurrency.
