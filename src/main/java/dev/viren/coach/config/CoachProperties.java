package dev.viren.coach.config;

import java.time.DayOfWeek;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code coach.*} block in application.yml. All values are set there. */
@ConfigurationProperties("coach")
public record CoachProperties(
        Goals goals,
        Queue queue,
        Outbox outbox,
        Nudge nudge,
        Difficulty difficulty,
        Recap recap,
        Sweeper sweeper,
        Ai ai,
        Push push) {

    public record Goals(int maxActivePerUser) {}

    public record Queue(String type, int capacity, int workersPerTopic, int maxAttempts, long retryBackoffMs) {}

    public record Outbox(long pollIntervalMs, int batchSize) {}

    public record Nudge(
            long schedulerIntervalMs,
            int batchSize,
            long ignoreAfterMinutes,
            long expiryIntervalMs,
            int fatigueThreshold,
            int minPoolSize,
            int topUpBatchSize,
            long emptyPoolRetryMinutes,
            long defaultSnoozeMinutes) {}

    public record Difficulty(int start, int min, int max, int levelUpAfterDone, int levelDownAfterIgnored) {}

    public record Recap(String cron, DayOfWeek dayOfWeek, int hour) {}

    public record Sweeper(long intervalMs, long staleAfterMs, int maxPlanAttempts) {}

    public record Ai(String provider, String planModel, String quickModel, long maxTokens, long timeoutSeconds, String apiKey) {}

    public record Push(String provider, Fcm fcm) {
        public record Fcm(String projectId, String serviceAccountJson) {}
    }
}
