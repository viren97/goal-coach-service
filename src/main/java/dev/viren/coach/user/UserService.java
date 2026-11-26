package dev.viren.coach.user;

import dev.viren.coach.common.error.CoachException;
import dev.viren.coach.common.error.ErrorCode;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final DeviceRepository devices;
    private final Clock clock;

    public UserService(UserRepository users, DeviceRepository devices, Clock clock) {
        this.users = users;
        this.devices = devices;
        this.clock = clock;
    }

    @Transactional
    public User create(String name, String email, String timezone, Intensity intensity, int windowStart, int windowEnd) {
        String normalisedEmail = email.trim().toLowerCase();
        if (users.existsByEmail(normalisedEmail)) {
            throw new CoachException(ErrorCode.DUPLICATE_RESOURCE, "User with email '" + normalisedEmail + "' already exists");
        }
        return users.save(new User(name.trim(), normalisedEmail, timezone, intensity, windowStart, windowEnd));
    }

    @Transactional(readOnly = true)
    public User get(String id) {
        return users.findById(id).orElseThrow(() -> CoachException.notFound("User", id));
    }

    @Transactional
    public User updatePreferences(String id, String timezone, Intensity intensity, Integer windowStart, Integer windowEnd) {
        User user = get(id);
        user.updatePreferences(timezone, intensity, windowStart, windowEnd);
        return user;
    }

    /** Idempotent on the token: re-registering the same token just refreshes it (and moves it to this user). */
    @Transactional
    public Device registerDevice(String userId, String token, DevicePlatform platform) {
        get(userId);
        return devices.findByToken(token)
                .map(d -> { d.reassign(userId, platform, clock.instant()); return d; })
                .orElseGet(() -> devices.save(new Device(userId, token, platform, clock.instant())));
    }

    @Transactional(readOnly = true)
    public List<Device> devices(String userId) {
        get(userId);
        return devices.findByUserId(userId);
    }

    @Transactional
    public void removeDevice(String userId, String deviceId) {
        Device device = devices.findById(deviceId)
                .filter(d -> d.getUserId().equals(userId))
                .orElseThrow(() -> CoachException.notFound("Device", deviceId));
        devices.delete(device);
    }
}
