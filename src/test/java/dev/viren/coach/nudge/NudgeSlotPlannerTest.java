package dev.viren.coach.nudge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class NudgeSlotPlannerTest {
    private final NudgeSlotPlanner planner = new NudgeSlotPlanner();
    private final ZoneId kolkata = ZoneId.of("Asia/Kolkata"); // UTC+05:30

    @Test
    void slotsAreSpreadEvenlyAcrossTheWindow() {
        // 08:00-20:00, 2 per day -> 11:00 and 17:00
        assertThat(planner.slotMinutes(8, 20, 2, null)).containsExactly(11 * 60, 17 * 60);
        // 1 per day -> middle of the window
        assertThat(planner.slotMinutes(8, 20, 1, null)).containsExactly(14 * 60);
    }

    @Test
    void learnedHourReplacesTheClosestSlot() {
        assertThat(planner.slotMinutes(8, 20, 2, 9)).containsExactly(9 * 60, 17 * 60);
        // outside the window -> ignored
        assertThat(planner.slotMinutes(8, 20, 2, 22)).containsExactly(11 * 60, 17 * 60);
    }

    @Test
    void nextSlotIsComputedInTheUsersZone() {
        // 10:00 IST on Monday = 04:30Z. Next slot 11:00 IST = 05:30Z.
        Instant after = Instant.parse("2026-11-23T04:30:00Z");
        assertThat(planner.nextSlot(kolkata, 8, 20, 2, null, after)).isEqualTo(Instant.parse("2026-11-23T05:30:00Z"));
    }

    @Test
    void rollsToTomorrowAfterTheLastSlot() {
        // 21:00 IST = 15:30Z; next is tomorrow 11:00 IST = 05:30Z
        Instant after = Instant.parse("2026-11-23T15:30:00Z");
        assertThat(planner.nextSlot(kolkata, 8, 20, 2, null, after)).isEqualTo(Instant.parse("2026-11-24T05:30:00Z"));
    }

    @Test
    void handlesDaylightSavingZones() {
        ZoneId ny = ZoneId.of("America/New_York");
        // DST ends 2026-11-01. 12:00 local on Nov 2 is 17:00Z (EST, UTC-5).
        Instant after = Instant.parse("2026-11-02T13:00:00Z");
        assertThat(planner.nextSlot(ny, 8, 16, 1, null, after)).isEqualTo(Instant.parse("2026-11-02T17:00:00Z"));
    }

    @Test
    void mostCommonHourNeedsEnoughSamples() {
        assertThat(ResponseTimeLearner.mostCommon(List.of(9, 9, 9))).isNull();
        assertThat(ResponseTimeLearner.mostCommon(List.of(9, 9, 18, 9, 18, 7))).isEqualTo(9);
    }
}
