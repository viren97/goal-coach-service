package dev.viren.coach.progress;

import java.time.LocalDate;
import java.util.Collection;
import java.util.TreeSet;

/** Streaks over local calendar days with at least one task done. Pure, so it is easy to test across timezones. */
public final class StreakCalculator {
    private StreakCalculator() {}

    public record Streaks(int current, int longest) {}

    /**
     * The current streak still counts if the last active day is yesterday (today isn't over yet).
     */
    public static Streaks compute(Collection<LocalDate> activeDays, LocalDate today) {
        TreeSet<LocalDate> days = new TreeSet<>(activeDays);
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : days) {
            run = (previous != null && previous.plusDays(1).equals(day)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }
        int current = 0;
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        while (days.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }
        return new Streaks(current, longest);
    }
}
