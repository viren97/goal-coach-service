package dev.viren.coach.task;

import dev.viren.coach.common.error.CoachException;
import dev.viren.coach.common.error.ErrorCode;
import java.time.Instant;

/**
 * State pattern for the task lifecycle:
 *
 * <pre>
 *   QUEUED ──schedule──▶ SCHEDULED ──markSent──▶ SENT ──expire──▶ EXPIRED
 *     ▲  ▲                  │  │                  │
 *     │  └────requeue───────┘  │                  │
 *     └─────────snooze─────────┴──────snooze──────┤
 *   complete / swap from QUEUED, SCHEDULED or SENT ──▶ DONE / SWAPPED
 * </pre>
 *
 * Each state only overrides the transitions it allows; anything else is rejected. Terminal states allow nothing.
 */
public abstract sealed class TaskState {

    private final TaskStatus status;

    protected TaskState(TaskStatus status) { this.status = status; }

    public TaskStatus status() { return status; }

    public void schedule(CoachTask t, Instant now) { reject(t, TaskStatus.SCHEDULED); }
    public void markSent(CoachTask t, boolean pushed, Instant now) { reject(t, TaskStatus.SENT); }
    public void complete(CoachTask t, Instant now) { reject(t, TaskStatus.DONE); }
    public void snooze(CoachTask t, Instant until, Instant now) { reject(t, TaskStatus.QUEUED); }
    public void swap(CoachTask t, Instant now) { reject(t, TaskStatus.SWAPPED); }
    public void expire(CoachTask t, Instant now) { reject(t, TaskStatus.EXPIRED); }
    public void requeue(CoachTask t, Instant now) { reject(t, TaskStatus.QUEUED); }

    private void reject(CoachTask t, TaskStatus target) {
        throw new CoachException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                "Task " + t.getId() + " cannot move from " + status + " to " + target);
    }

    public static TaskState of(TaskStatus status) {
        return switch (status) {
            case QUEUED -> Queued.INSTANCE;
            case SCHEDULED -> Scheduled.INSTANCE;
            case SENT -> Sent.INSTANCE;
            case DONE -> Terminal.DONE;
            case SWAPPED -> Terminal.SWAPPED;
            case EXPIRED -> Terminal.EXPIRED;
        };
    }

    /** Transitions shared by every non-terminal state: the user can always finish, swap or snooze an open task. */
    abstract static sealed class Open extends TaskState {
        Open(TaskStatus status) { super(status); }

        @Override public void complete(CoachTask t, Instant now) { t.moveTo(TaskStatus.DONE, now); t.setActedAt(now); }
        @Override public void swap(CoachTask t, Instant now) { t.moveTo(TaskStatus.SWAPPED, now); t.setActedAt(now); }
        @Override public void snooze(CoachTask t, Instant until, Instant now) {
            t.snoozeUntil(until);
            t.moveTo(TaskStatus.QUEUED, now);
        }
    }

    static final class Queued extends Open {
        static final Queued INSTANCE = new Queued();
        private Queued() { super(TaskStatus.QUEUED); }

        @Override public void schedule(CoachTask t, Instant now) { t.moveTo(TaskStatus.SCHEDULED, now); }
    }

    static final class Scheduled extends Open {
        static final Scheduled INSTANCE = new Scheduled();
        private Scheduled() { super(TaskStatus.SCHEDULED); }

        @Override public void markSent(CoachTask t, boolean pushed, Instant now) {
            t.recordSent(pushed, now);
            t.moveTo(TaskStatus.SENT, now);
        }
        @Override public void requeue(CoachTask t, Instant now) { t.moveTo(TaskStatus.QUEUED, now); }
    }

    static final class Sent extends Open {
        static final Sent INSTANCE = new Sent();
        private Sent() { super(TaskStatus.SENT); }

        @Override public void expire(CoachTask t, Instant now) { t.moveTo(TaskStatus.EXPIRED, now); }
    }

    static final class Terminal extends TaskState {
        static final Terminal DONE = new Terminal(TaskStatus.DONE);
        static final Terminal SWAPPED = new Terminal(TaskStatus.SWAPPED);
        static final Terminal EXPIRED = new Terminal(TaskStatus.EXPIRED);
        private Terminal(TaskStatus status) { super(status); }
    }
}
