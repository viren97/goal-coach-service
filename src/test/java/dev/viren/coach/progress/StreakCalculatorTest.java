package dev.viren.coach.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreakCalculatorTest {
    private final LocalDate today = LocalDate.of(2026, 11, 26);

    @Test
    void noActivityNoStreak() {
        assertThat(StreakCalculator.compute(List.of(), today)).isEqualTo(new StreakCalculator.Streaks(0, 0));
    }

    @Test
    void consecutiveDaysEndingToday() {
        var s = StreakCalculator.compute(List.of(today, today.minusDays(1), today.minusDays(2), today), today);
        assertThat(s).isEqualTo(new StreakCalculator.Streaks(3, 3));
    }

    @Test
    void streakSurvivesUntilTodayIsOver() {
        var s = StreakCalculator.compute(List.of(today.minusDays(1), today.minusDays(2)), today);
        assertThat(s.current()).isEqualTo(2);
    }

    @Test
    void gapResetsCurrentButKeepsLongest() {
        var s = StreakCalculator.compute(List.of(
                today.minusDays(10), today.minusDays(9), today.minusDays(8), today.minusDays(7),
                today.minusDays(3)), today);
        assertThat(s).isEqualTo(new StreakCalculator.Streaks(0, 4));
    }
}
