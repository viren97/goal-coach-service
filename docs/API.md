# API Reference

Base URL: `http://localhost:8080/api/v1` · JSON in and out · times are ISO-8601 UTC instants.

## Conventions

**Idempotency.** `POST /users/{userId}/goals` requires an `Idempotency-Key` header (1–100 chars).

| Situation | Response |
|---|---|
| New key | `202 Accepted`, goal in `PLANNING` (the plan is generated in the background) |
| Same key, same body | `200 OK`, the original goal |
| Same key, different body | `422 IDEMPOTENCY_KEY_REUSED` |

Task actions are idempotent by state: sending `DONE` to a task that is already `DONE` (or `SWAP` to one that is
already `SWAPPED`) returns `200` with the task unchanged, so a notification button and an in-app tap can both fire.
Device registration is idempotent on the token.

**Async processing.** Poll `GET /goals/{id}` until `status` is `ACTIVE` (usually under a second with the
template coach, a few seconds with Claude). The first task is pushed right after.

**Errors.** Every error has this shape:
```json
{ "code": "GOAL_LIMIT_REACHED", "message": "…", "details": [], "timestamp": "2026-11-26T09:59:16Z" }
```

| Code | HTTP | Meaning |
|---|---|---|
| `INVALID_REQUEST` | 400 | Bean validation, malformed JSON, missing header, unknown timezone, bad window |
| `RESOURCE_NOT_FOUND` | 404 | User / goal / task / device not found |
| `DUPLICATE_RESOURCE` | 409 | Email already registered |
| `GOAL_LIMIT_REACHED` | 409 | User already has the maximum number of open goals (`coach.goals.max-active-per-user`) |
| `ILLEGAL_STATE_TRANSITION` | 409 | e.g. snoozing a task that is already done, pausing a goal that isn't active |
| `CONCURRENCY_CONFLICT` | 409 | Concurrent update that outlasted internal retries; retry |
| `IDEMPOTENCY_KEY_REUSED` | 422 | Key reused with a different payload |
| `INTERNAL_ERROR` | 500 | Unexpected error (details logged, not exposed) |

---

## Users

### `POST /users`
```bash
curl -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' -d '{
  "name": "Asha", "email": "asha@example.com", "timezone": "Asia/Kolkata",
  "intensity": "STEADY", "windowStartHour": 8, "windowEndHour": 21 }'
```

| Field | Notes |
|---|---|
| `timezone` | IANA zone id. All nudge times are computed in this zone |
| `intensity` | `CHILL` (1 nudge/day) · `STEADY` (2) · `INTENSE` (3) |
| `windowStartHour`, `windowEndHour` | Local hours `[start, end)`, `0 <= start < end <= 24` |

`201` → `{ id, name, email, timezone, intensity, windowStartHour, windowEndHour, createdAt }` · `409` if the email exists.

| Endpoint | Description |
|---|---|
| `GET /users/{id}` | The user |
| `PATCH /users/{id}/preferences` | Any of `timezone`, `intensity`, `windowStartHour`, `windowEndHour` |
| `POST /users/{id}/devices` | `{ "token": "<FCM registration token>", "platform": "ANDROID" \| "IOS" \| "WEB" }` → `201` |
| `GET /users/{id}/devices` | Registered devices (tokens are not returned) |
| `DELETE /users/{id}/devices/{deviceId}` | `204` (call on sign-out) |

## Goals

### `POST /users/{userId}/goals`
Headers: `Idempotency-Key` (required).
```bash
curl -X POST localhost:8080/api/v1/users/$USER/goals -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: onboarding-1' \
  -d '{"identity":"a confident public speaker","context":"I freeze in meetings with more than 5 people"}'
```

| Field | Required | Notes |
|---|---|---|
| `identity` | yes | Who the user wants to become, ≤ 300 chars |
| `context` | no | Anything that helps the plan (level, constraints), ≤ 2000 chars |

`202` →
```json
{ "id": "…", "userId": "…", "identity": "a confident public speaker", "status": "PLANNING",
  "planSummary": null, "currentDifficulty": 2, "ignoredStreak": 0, "nextNudgeAt": null, "milestones": [] }
```

Goal `status`: `PLANNING` → `ACTIVE` ⇄ `PAUSED` → `COMPLETED`; `PLAN_FAILED` if planning never succeeded.

| Endpoint | Description |
|---|---|
| `GET /users/{userId}/goals` | A user's goals, newest first |
| `GET /goals/{id}` | Goal with milestones (`position, title, description, targetDone, status`) |
| `POST /goals/{id}/pause` | Stop nudges (`ACTIVE` → `PAUSED`) |
| `POST /goals/{id}/resume` | Resume; the next task is sent right away |
| `POST /goals/{id}/check-in` | Answer to the fatigue check-in push: `{ "answer": "EASIER" \| "KEEP" \| "PAUSE" }` |

## Tasks

| Endpoint | Description |
|---|---|
| `GET /goals/{goalId}/today` | The most recently sent open task, or `204` if there is none |
| `GET /goals/{goalId}/tasks?status=QUEUED` | All tasks of a goal, optionally filtered by status |
| `GET /tasks/{id}` | One task |

Task `status`: `QUEUED`, `SCHEDULED`, `SENT`, `DONE`, `SWAPPED`, `EXPIRED`. `kind`: `LEARN` or `DO`.

### `POST /tasks/{id}/actions`
Called by the app and by the notification's action buttons.
```bash
curl -X POST localhost:8080/api/v1/tasks/$TASK/actions -H 'Content-Type: application/json' \
  -d '{"action":"DONE","note":"Recorded it twice","proofUrl":"https://photos.example.com/abc"}'
```

| `action` | Effect |
|---|---|
| `DONE` | Completes the task, can level up difficulty, can complete the milestone/goal |
| `SNOOZE` | Back to the pool, not picked before `snoozeMinutes` (default 120, 5–10080) |
| `SWAP` | Replaces the task: the next one is pushed within one scheduler tick |
| `TOO_HARD` | Like `SWAP`, and lowers difficulty one level |

`200` →
```json
{ "id": "…", "kind": "DO", "title": "Record a 60-second video explaining your day", "difficulty": 2,
  "estMinutes": 10, "status": "DONE", "sentAt": "…", "actedAt": "…", "snoozeCount": 0, "note": "Recorded it twice" }
```

## Progress & recaps

### `GET /goals/{goalId}/progress`
```json
{ "goalId": "…", "identity": "a confident public speaker", "status": "ACTIVE",
  "tasksDone": 7, "identityScore": 19, "currentStreakDays": 4, "longestStreakDays": 4,
  "currentDifficulty": 3, "pushActionRate": 0.83,
  "milestones": [ { "title": "Foundations", "status": "COMPLETED", "done": 5, "target": 5 },
                  { "title": "Building the habit", "status": "ACTIVE", "done": 2, "target": 5 } ] }
```
`identityScore` is the sum of the difficulty of every completed task. `pushActionRate` is the share of pushes in
the last 7 days that led to an action, or `null` if no pushes were sent.

### `GET /goals/{goalId}/recaps`
Weekly recaps, newest first: `{ weekStart, message, highlights[], tasksDone, difficultyChange }`.

## Push notifications (contract with the mobile app)

| `category` | When | Buttons the app should render | Data |
|---|---|---|---|
| `COACH_TASK` | A task nudge | Done · Snooze · Swap (→ `POST /tasks/{taskId}/actions`) | `taskId`, `goalId`, `kind`, `deepLink`, `resourceUrl?` |
| `COACH_CHECKIN` | 3 ignored pushes in a row | Easier · Keep · Pause (→ `POST /goals/{goalId}/check-in`) | `goalId`, `deepLink` |
| `COACH_INFO` | Milestone reached, goal completed, weekly recap | none | `goalId`, `deepLink` |

The same task is always sent with the collapse key `task-{taskId}`, so a retried send replaces the notification
instead of duplicating it.

## Operations

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Overall health |
| `GET /actuator/health/liveness`, `/readiness` | Kubernetes probes |
