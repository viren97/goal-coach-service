package dev.viren.coach.outbox;

import dev.viren.coach.queue.QueueMessage;
import dev.viren.coach.queue.TaskQueue;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls NEW outbox rows and hands them to the queue. Publishing happens outside a DB transaction
 * (no I/O while holding a transaction). If we crash between publish and markPublished the message is
 * re-sent: at-least-once delivery, consumers are idempotent.
 */
@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxService outbox;
    private final TaskQueue queue;
    private final int batchSize;

    public OutboxRelay(OutboxService outbox, TaskQueue queue, @Value("${coach.outbox.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.queue = queue;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${coach.outbox.poll-interval-ms:200}")
    public void relay() {
        List<OutboxEvent> batch = outbox.pending(batchSize);
        if (batch.isEmpty()) return;

        List<String> published = new ArrayList<>();
        for (OutboxEvent event : batch) {
            try {
                queue.publish(new QueueMessage(event.getEventType().topic(), event.getAggregateId(),
                        event.getEventType().name(), event.getPayload()));
                published.add(event.getId());
            } catch (Exception e) {
                log.warn("Outbox relay paused, queue rejected event {}: {}", event.getId(), e.getMessage());
                break; // keep ordering; retry the rest on the next tick
            }
        }
        outbox.markPublished(published);
    }
}
