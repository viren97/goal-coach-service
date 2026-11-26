package dev.viren.coach.event;

import dev.viren.coach.outbox.OutboxEventType;
import dev.viren.coach.outbox.OutboxService;
import org.springframework.stereotype.Component;

/** Writes events to the outbox, inside the caller's transaction (the state change and its event commit together). */
@Component
public class CoachEventPublisher {
    private final OutboxService outbox;

    public CoachEventPublisher(OutboxService outbox) {
        this.outbox = outbox;
    }

    public void publish(CoachEvent event) {
        outbox.record(OutboxEventType.COACH_EVENT, event.goalId(), event);
    }
}
