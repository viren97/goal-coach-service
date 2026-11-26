package dev.viren.coach.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * App-assigned UUID ids + nullable @Version: Spring Data treats {@code version == null} as "new",
 * so save() does a plain INSERT instead of SELECT-then-merge. The version also gives
 * optimistic locking for free on every entity.
 */
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
