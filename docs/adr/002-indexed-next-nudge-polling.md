# ADR-002: Schedule nudges by polling an indexed `next_nudge_at` column

**Status:** Accepted

## Context

Each user gets 1–3 nudges a day at times in their own time zone and window, shifted toward the hour they usually
respond. The timing changes all the time:

- A swap makes the goal due immediately.
- Fatigue backs off to one nudge a day.
- Pausing stops nudges entirely.
- Changing preferences moves every future slot.

The scheduler must survive restarts and run safely on several replicas.

## Options

| Option | Problem |
|---|---|
| A cron job per user | Millions of jobs; rescheduling on every change is painful; state lives outside the DB |
| Delayed messages (Kafka has none built in; SQS has a 15-minute cap) | Can't express "tomorrow 18:00 IST", and cancelling a scheduled message is hard |
| Scan every active goal each tick | Cost grows with total users, not with the work actually due |
| **Store `next_nudge_at` on the goal and poll `status = ACTIVE AND next_nudge_at <= now`** | Polling latency |

## Decision

`Goal.nextNudgeAt` is a UTC instant, indexed together with `status`. `NudgeSlotPlanner` computes it in the user's
zone: slots are spread evenly over the window, and the closest slot moves to the learned response hour.
`NudgeScheduler` runs every 30 s and takes up to 100 due goals. For each one, `NudgeWriter.scheduleDueNudge`
does the following in a single transaction:

1. Picks a task.
2. Moves it `QUEUED → SCHEDULED`.
3. Writes `SEND_NUDGE` to the outbox.
4. Stores the next slot.

Any change to the timing (a swap, a check-in, pause or resume) is simply a new value in that column.

## Consequences

- ✅ Cost scales with due goals, and the state lives in the source of truth (it survives restarts).
- ✅ Safe on many replicas: two schedulers that read the same goal race on its `@Version`, and only one commits.
- ✅ Easy to test: the scheduler takes an injected `Clock`, and the tests move time.
- ⚠️ Up to one tick (30 s) of latency, which is fine for coaching nudges.
- ⚠️ At very large scale the scan should be sharded by goal-id hash across instances (roadmap).
