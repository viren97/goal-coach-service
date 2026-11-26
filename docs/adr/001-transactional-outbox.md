# ADR-001: Transactional outbox for every command and event

**Status:** Accepted

## Context

Almost every user action triggers follow-up work that must not be lost or invented:

- Creating a goal must lead to plan generation.
- Scheduling a nudge must lead to a push.
- Completing a task must lead to a milestone celebration.

The obvious implementation writes to the database and then publishes to Kafka. That is a *dual write*:

- If the process crashes between the two, the goal exists but is never planned.
- If the publish succeeds and the DB transaction rolls back, a push goes out for a task that was never scheduled.

## Options

| Option | Problem |
|---|---|
| Write the DB, then publish to Kafka | Dual write: a lost or phantom message on every crash in between |
| Publish first, then write the DB | Same problem in reverse |
| Distributed transaction (XA) | Not supported by Kafka in a useful way; heavy and slow |
| Change data capture (Debezium) | Correct, but adds connector infrastructure that a small team has to run |
| **Transactional outbox + relay** | One extra table and a poller |

## Decision

Every command (`GENERATE_PLAN`, `SEND_NUDGE`, `TOP_UP_TASKS`, `GENERATE_RECAP`) and every event
(`COACH_EVENT`) is written to `outbox_events` **in the same DB transaction** as the state change.
`OutboxService.record` uses `Propagation.MANDATORY`, so calling it outside a transaction fails loudly.

`OutboxRelay` polls `NEW` rows in creation order and publishes them with the goal id as the key, so one goal's
messages stay ordered on one partition. It marks a row `PUBLISHED` only after the broker acks. If a publish
fails, it stops at that row so ordering is kept.

## Consequences

- ✅ No lost or phantom work. If Kafka is down the API keeps accepting writes, and the rows wait.
- ✅ The same code works with the in-memory queue (tests, local runs) and Kafka.
- ⚠️ Delivery is **at-least-once**: a crash after publishing but before marking the row re-sends it. Every handler
  is therefore idempotent and re-checks state before acting. For example, `SendNudgeHandler` skips tasks that are
  no longer `SCHEDULED`, and `PlanWriter` applies a plan only while the goal is `PLANNING`.
- ⚠️ Adds up to one poll interval (200 ms) of latency, and a table that needs periodic cleanup of published rows (roadmap).
