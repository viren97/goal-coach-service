package dev.viren.coach.event;

import java.time.Instant;

/**
 * Public event contract on {@code coach.events}. {@code title} / {@code message} carry the human-readable part
 * (milestone name, recap text) so subscribers such as notifications don't need to re-query.
 */
public record CoachEvent(
        CoachEventType type,
        String goalId,
        String userId,
        String taskId,
        String title,
        String message,
        Instant occurredAt) {}
