package dev.viren.coach.user;

import dev.viren.coach.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** A push target (FCM registration token). One user can have several devices. */
@Entity
@Table(name = "devices", indexes = @Index(columnList = "user_id"))
public class Device extends BaseEntity {
    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DevicePlatform platform;

    @Column(nullable = false)
    private Instant lastSeenAt;

    protected Device() {}

    public Device(String userId, String token, DevicePlatform platform, Instant now) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
        this.lastSeenAt = now;
    }

    /** Tokens move between users when someone signs into the same phone with another account. */
    public void reassign(String userId, DevicePlatform platform, Instant now) {
        this.userId = userId;
        this.platform = platform;
        this.lastSeenAt = now;
    }

    public String getUserId() { return userId; }
    public String getToken() { return token; }
    public DevicePlatform getPlatform() { return platform; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
