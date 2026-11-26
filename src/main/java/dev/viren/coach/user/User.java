package dev.viren.coach.user;

import dev.viren.coach.common.error.CoachException;
import dev.viren.coach.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.DateTimeException;
import java.time.ZoneId;

@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    /** IANA zone id, e.g. Asia/Kolkata. All schedules are computed in the user's local time and stored in UTC. */
    @Column(nullable = false)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Intensity intensity;

    /** Local hours [start, end) during which nudges may be sent. */
    @Column(nullable = false)
    private int windowStartHour;

    @Column(nullable = false)
    private int windowEndHour;

    protected User() {}

    public User(String name, String email, String timezone, Intensity intensity, int windowStartHour, int windowEndHour) {
        this.name = name;
        this.email = email;
        updatePreferences(timezone, intensity, windowStartHour, windowEndHour);
    }

    public void updatePreferences(String timezone, Intensity intensity, Integer windowStartHour, Integer windowEndHour) {
        String tz = timezone != null ? timezone : this.timezone;
        int start = windowStartHour != null ? windowStartHour : this.windowStartHour;
        int end = windowEndHour != null ? windowEndHour : this.windowEndHour;
        try {
            ZoneId.of(tz);
        } catch (DateTimeException | NullPointerException e) {
            throw CoachException.invalid("Unknown timezone '" + tz + "'");
        }
        if (start < 0 || end > 24 || start >= end) {
            throw CoachException.invalid("Send window must satisfy 0 <= start < end <= 24");
        }
        this.timezone = tz;
        this.windowStartHour = start;
        this.windowEndHour = end;
        if (intensity != null) this.intensity = intensity;
    }

    public ZoneId zone() { return ZoneId.of(timezone); }

    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getTimezone() { return timezone; }
    public Intensity getIntensity() { return intensity; }
    public int getWindowStartHour() { return windowStartHour; }
    public int getWindowEndHour() { return windowEndHour; }
}
