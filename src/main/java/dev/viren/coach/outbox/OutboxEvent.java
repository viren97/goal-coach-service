package dev.viren.coach.outbox;

import dev.viren.coach.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "outbox_events", indexes = @Index(columnList = "status, created_at"))
public class OutboxEvent extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventType eventType;

    /** Partition key: the goal id, so a goal's commands and events stay ordered. */
    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.NEW;

    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(OutboxEventType eventType, String aggregateId, String payload) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    public void markPublished() {
        status = OutboxStatus.PUBLISHED;
        publishedAt = Instant.now();
    }

    public OutboxEventType getEventType() { return eventType; }
    public String getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
}
