package dev.viren.coach.nudge;

import dev.viren.coach.config.CoachProperties;
import dev.viren.coach.user.Intensity;
import org.springframework.stereotype.Component;

/**
 * Protects against notification fatigue, the main reason apps like this get muted. After
 * {@code fatigue-threshold} ignored pushes in a row the coach backs off to one nudge a day and asks once
 * whether to make things easier. Any user action resets the streak.
 */
@Component
public class FatigueGuard {
    private final int threshold;

    public FatigueGuard(CoachProperties properties) {
        this.threshold = properties.nudge().fatigueThreshold();
    }

    public boolean isFatigued(int ignoredStreak) {
        return ignoredStreak >= threshold;
    }

    /** True exactly once per fatigue episode: when the streak first reaches the threshold. */
    public boolean shouldCheckIn(int ignoredStreak) {
        return ignoredStreak == threshold;
    }

    public int pushesPerDay(Intensity intensity, int ignoredStreak) {
        return isFatigued(ignoredStreak) ? 1 : intensity.pushesPerDay();
    }
}
