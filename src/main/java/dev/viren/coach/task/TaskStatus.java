package dev.viren.coach.task;

public enum TaskStatus {
    /** In the pool of the milestone, waiting for a slot. */
    QUEUED,
    /** Picked for a nudge; the SEND_NUDGE command is in the outbox/queue. */
    SCHEDULED,
    /** Push delivered (or shown as today's task when the user has no device). */
    SENT,
    DONE,
    /** Replaced at the user's request (swap / too hard). */
    SWAPPED,
    /** Sent and ignored past coach.nudge.ignore-after-minutes. */
    EXPIRED;

    public boolean isTerminal() {
        return this == DONE || this == SWAPPED || this == EXPIRED;
    }
}
