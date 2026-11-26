package dev.viren.coach.task;

import static org.assertj.core.api.Assertions.assertThat;

import dev.viren.coach.support.TestProperties;
import org.junit.jupiter.api.Test;

class DifficultyPolicyTest {
    private final DifficultyPolicy policy = new DifficultyPolicy(TestProperties.defaults()); // up after 3, down after 2 ignored

    @Test
    void threeDoneInARowLevelsUp() {
        DifficultyState s = new DifficultyState(2, 0, 0);
        s = policy.onDone(s);
        s = policy.onDone(s);
        assertThat(s.difficulty()).isEqualTo(2);
        s = policy.onDone(s);
        assertThat(s).isEqualTo(new DifficultyState(3, 0, 0));
    }

    @Test
    void neverAboveMaxOrBelowMin() {
        DifficultyState top = new DifficultyState(5, 2, 0);
        assertThat(policy.onDone(top).difficulty()).isEqualTo(5);
        DifficultyState bottom = new DifficultyState(1, 0, 0);
        assertThat(policy.onTooHard(bottom).difficulty()).isEqualTo(1);
        assertThat(policy.onEasier(bottom).difficulty()).isEqualTo(1);
    }

    @Test
    void tooHardLevelsDownImmediately() {
        assertThat(policy.onTooHard(new DifficultyState(3, 2, 1))).isEqualTo(new DifficultyState(2, 0, 0));
    }

    @Test
    void everySecondIgnoredPushLevelsDownAndStreakKeepsCounting() {
        DifficultyState s = new DifficultyState(3, 1, 0);
        s = policy.onIgnored(s);
        assertThat(s).isEqualTo(new DifficultyState(3, 0, 1));
        s = policy.onIgnored(s);
        assertThat(s).isEqualTo(new DifficultyState(2, 0, 2));
        s = policy.onIgnored(s);
        assertThat(s.ignoredStreak()).isEqualTo(3);
    }

    @Test
    void anyEngagementResetsTheIgnoredStreak() {
        DifficultyState fatigued = new DifficultyState(2, 0, 4);
        assertThat(policy.onEngaged(fatigued).ignoredStreak()).isZero();
        assertThat(policy.onDone(fatigued).ignoredStreak()).isZero();
    }

    @Test
    void recapShiftIsBoundedToOneStep() {
        DifficultyState s = new DifficultyState(3, 2, 0);
        assertThat(policy.shift(s, 5).difficulty()).isEqualTo(4);
        assertThat(policy.shift(s, -9).difficulty()).isEqualTo(2);
        assertThat(policy.shift(s, 0)).isEqualTo(s);
    }
}
