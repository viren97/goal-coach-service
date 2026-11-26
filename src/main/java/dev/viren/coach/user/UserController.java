package dev.viren.coach.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public record CreateUserRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank String timezone,
            @NotNull Intensity intensity,
            @NotNull @Min(0) @Max(23) Integer windowStartHour,
            @NotNull @Min(1) @Max(24) Integer windowEndHour) {}

    public record PreferencesRequest(
            String timezone,
            Intensity intensity,
            @Min(0) @Max(23) Integer windowStartHour,
            @Min(1) @Max(24) Integer windowEndHour) {}

    public record RegisterDeviceRequest(@NotBlank @Size(max = 512) String token, @NotNull DevicePlatform platform) {}

    public record UserResponse(String id, String name, String email, String timezone, Intensity intensity,
                               int windowStartHour, int windowEndHour, Instant createdAt) {
        static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getTimezone(), u.getIntensity(),
                    u.getWindowStartHour(), u.getWindowEndHour(), u.getCreatedAt());
        }
    }

    public record DeviceResponse(String id, String userId, DevicePlatform platform, Instant lastSeenAt) {
        static DeviceResponse from(Device d) {
            return new DeviceResponse(d.getId(), d.getUserId(), d.getPlatform(), d.getLastSeenAt());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest r) {
        return UserResponse.from(userService.create(r.name(), r.email(), r.timezone(), r.intensity(),
                r.windowStartHour(), r.windowEndHour()));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable String id) {
        return UserResponse.from(userService.get(id));
    }

    @PatchMapping("/{id}/preferences")
    public UserResponse updatePreferences(@PathVariable String id, @Valid @RequestBody PreferencesRequest r) {
        return UserResponse.from(userService.updatePreferences(id, r.timezone(), r.intensity(),
                r.windowStartHour(), r.windowEndHour()));
    }

    @PostMapping("/{id}/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse registerDevice(@PathVariable String id, @Valid @RequestBody RegisterDeviceRequest r) {
        return DeviceResponse.from(userService.registerDevice(id, r.token(), r.platform()));
    }

    @GetMapping("/{id}/devices")
    public List<DeviceResponse> devices(@PathVariable String id) {
        return userService.devices(id).stream().map(DeviceResponse::from).toList();
    }

    @DeleteMapping("/{id}/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDevice(@PathVariable String id, @PathVariable String deviceId) {
        userService.removeDevice(id, deviceId);
    }
}
