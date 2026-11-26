# ADR-003: Optimistic locking with whole-transaction retry instead of row locks

**Status:** Accepted

## Context

The same goal row is written by several actors:

- the user's actions (from the app or a notification button)
- the nudge scheduler
- the expiry job
- the recap writer
- the sweeper

They run on different threads and pods. Lost updates are not acceptable: for example, a DONE that resets the
ignored streak must not be overwritten by an expiry that increments it.

## Options

| Option | Problem |
|---|---|
| `SELECT … FOR UPDATE` | Lock waits and possible deadlocks; holds connections while waiting; harder to get right across code paths |
| Last-write-wins | Silent lost updates |
| Distributed lock (Redis) | Extra dependency and failure mode for a problem the database already solves |
| **`@Version` optimistic locking + retry** | Conflicts surface as exceptions that someone must handle |

## Decision

Every entity extends `BaseEntity` with a `@Version` column. A conflicting commit fails with
`OptimisticLockingFailureException`. Each caller handles it in the way that fits:

- **User actions** (`TaskService.act`): retry the *whole* transaction up to 3 times with a short backoff. Every
  attempt re-reads fresh rows. If the conflict persists, it surfaces as `409 CONCURRENCY_CONFLICT`.
- **Background jobs** (scheduler, expiry): skip that row on a conflict; the next tick retries it.
- **Racing workers** (duplicate plan or recap commands): the loser's commit fails and its re-check finds the work done.

## Consequences

- ✅ No lock waits in the common case. Conflicts are rare because a goal belongs to one user.
- ✅ Correct across any number of replicas without extra infrastructure.
- ⚠️ Every write path must handle conflicts on purpose. That handling is centralised in the services listed above.
