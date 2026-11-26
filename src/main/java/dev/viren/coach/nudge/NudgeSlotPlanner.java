package dev.viren.coach.nudge;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Decides when the next nudge goes out. Slots are spread evenly across the user's local send window
 * (n = pushes per day); if we have learned the hour the user usually acts, the closest slot moves to that hour.
 * Pure and timezone-aware: everything is computed in the user's zone and returned as a UTC instant.
 */
@Component
public class NudgeSlotPlanner {

    public Instant nextSlot(ZoneId zone, int windowStartHour, int windowEndHour, int pushesPerDay,
                            Integer preferredHour, Instant after) {
        List<Integer> slots = slotMinutes(windowStartHour, windowEndHour, pushesPerDay, preferredHour);
        LocalDate day = after.atZone(zone).toLocalDate();
        for (int d = 0; d < 3; d++) {
            for (int minute : slots) {
                Instant candidate = day.plusDays(d).atTime(LocalTime.of(minute / 60, minute % 60)).atZone(zone).toInstant();
                if (candidate.isAfter(after)) return candidate;
            }
        }
        throw new IllegalStateException("No slot found within 3 days"); // unreachable with a valid window
    }

    /** Minutes after local midnight, ascending. */
    List<Integer> slotMinutes(int startHour, int endHour, int pushesPerDay, Integer preferredHour) {
        int n = Math.max(1, pushesPerDay);
        int start = startHour * 60;
        int length = (endHour - startHour) * 60;
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            slots.add(start + (int) ((i + 0.5) * length / n));
        }
        if (preferredHour != null && preferredHour >= startHour && preferredHour < endHour) {
            int preferred = preferredHour * 60;
            int closest = 0;
            for (int i = 1; i < n; i++) {
                if (Math.abs(slots.get(i) - preferred) < Math.abs(slots.get(closest) - preferred)) closest = i;
            }
            slots.set(closest, preferred);
            slots.sort(Integer::compareTo);
        }
        return slots;
    }
}
