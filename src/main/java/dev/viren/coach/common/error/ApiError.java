package dev.viren.coach.common.error;

import java.time.Instant;
import java.util.List;

public record ApiError(String code, String message, List<String> details, Instant timestamp) {
    public ApiError(String code, String message, List<String> details) {
        this(code, message, details, Instant.now());
    }
}
