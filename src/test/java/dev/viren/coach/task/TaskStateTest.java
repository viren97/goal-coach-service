package dev.viren.coach.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.viren.coach.common.error.CoachException;
import dev.viren.coach.common.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TaskStateTest {
    private final Instant now = Instant.parse("2026-11-23T10:00:00Z");

    private CoachTask task() {
        return new CoachTask("g", "m", "u", TaskKind.DO, "Do it", "body", null, 2, 10, 1, TaskOrigin.PLAN, now);
    }

    @Test
    void happyPathQueuedScheduledSentDone() {
        CoachTask t = task();
        t.schedule(now);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        t.markSent(true, now);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.SENT);
        assertThat(t.isPushed()).isTrue();
        assertThat(t.getSentAt()).isEqualTo(now);
        t.complete("felt good", "https://example.com/proof", now);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(t.getActedAt()).isEqualTo(now);
        assertThat(t.getNote()).isEqualTo("felt good");
    }

    @Test
    void snoozeReturnsToPoolUntilLater() {
        CoachTask t = task();
        t.schedule(now);
        t.markSent(true, now);
        Instant until = now.plus(Duration.ofHours(2));
        t.snooze(until, now);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(t.getSnoozeCount()).isEqualTo(1);
        assertThat(t.isAvailableAt(now)).isFalse();
        assertThat(t.isAvailableAt(until)).isTrue();
    }

    @Test
    void openTasksCanBeDoneOrSwappedFromTheApp() {
        CoachTask queued = task();
        queued.complete(null, null, now);
        assertThat(queued.getStatus()).isEqualTo(TaskStatus.DONE);

        CoachTask scheduled = task();
        scheduled.schedule(now);
        scheduled.swap(now);
        assertThat(scheduled.getStatus()).isEqualTo(TaskStatus.SWAPPED);
    }

    @Test
    void onlySentTasksExpireAndOnlyScheduledRequeue() {
        CoachTask t = task();
        assertThatThrownBy(() -> t.expire(now)).isInstanceOf(CoachException.class);
        assertThatThrownBy(() -> t.requeue(now)).isInstanceOf(CoachException.class);
        assertThatThrownBy(() -> t.markSent(true, now)).isInstanceOf(CoachException.class);
        t.schedule(now);
        t.requeue(now);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.QUEUED);
    }

    @Test
    void terminalStatesRejectEverything() {
        CoachTask t = task();
        t.complete(null, null, now);
        assertThatThrownBy(() -> t.swap(now))
                .isInstanceOf(CoachException.class)
                .satisfies(e -> assertThat(((CoachException) e).code()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
        assertThatThrownBy(() -> t.snooze(now, now)).isInstanceOf(CoachException.class);
        assertThatThrownBy(() -> t.schedule(now)).isInstanceOf(CoachException.class);
        assertThatThrownBy(() -> t.complete(null, null, now)).isInstanceOf(CoachException.class);
    }
}
