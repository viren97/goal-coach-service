package dev.viren.coach.task;

import dev.viren.coach.config.CoachProperties;
import org.springframework.stereotype.Component;

/**
 * Keeps the user in the zone where they can still win: finishing tasks makes them harder, skipping them makes
 * them smaller. Pure function of the current state, so it is trivially unit-tested.
 *
 * <ul>
 *   <li>DONE: +1 done at level; after {@code levelUpAfterDone} in a row the level goes up.</li>
 *   <li>TOO_HARD: level down immediately.</li>
 *   <li>Ignored (expired push): after {@code levelDownAfterIgnored} in a row the level goes down.</li>
 *   <li>Any action (done, snooze, swap) resets the ignored streak: the user is engaged.</li>
 * </ul>
 */
@Component
public class DifficultyPolicy {
    private final CoachProperties.Difficulty config;

    public DifficultyPolicy(CoachProperties properties) {
        this.config = properties.difficulty();
    }

    public int start() {
        return config.start();
    }

    public DifficultyState onDone(DifficultyState s) {
        int done = s.doneAtLevel() + 1;
        if (done >= config.levelUpAfterDone() && s.difficulty() < config.max()) {
            return new DifficultyState(s.difficulty() + 1, 0, 0);
        }
        return new DifficultyState(s.difficulty(), done, 0);
    }

    public DifficultyState onTooHard(DifficultyState s) {
        return new DifficultyState(Math.max(config.min(), s.difficulty() - 1), 0, 0);
    }

    public DifficultyState onEngaged(DifficultyState s) {
        return new DifficultyState(s.difficulty(), s.doneAtLevel(), 0);
    }

    public DifficultyState onIgnored(DifficultyState s) {
        int ignored = s.ignoredStreak() + 1;
        if (ignored % config.levelDownAfterIgnored() == 0 && s.difficulty() > config.min()) {
            return new DifficultyState(s.difficulty() - 1, 0, ignored);
        }
        return new DifficultyState(s.difficulty(), 0, ignored);
    }

    /** The user answered "make it easier" to a fatigue check-in. */
    public DifficultyState onEasier(DifficultyState s) {
        return new DifficultyState(Math.max(config.min(), s.difficulty() - 1), 0, 0);
    }

    /** A weekly recap may nudge the level by -1, 0 or +1. */
    public DifficultyState shift(DifficultyState s, int delta) {
        int bounded = Math.max(-1, Math.min(1, delta));
        int level = Math.max(config.min(), Math.min(config.max(), s.difficulty() + bounded));
        return new DifficultyState(level, level == s.difficulty() ? s.doneAtLevel() : 0, s.ignoredStreak());
    }
}
