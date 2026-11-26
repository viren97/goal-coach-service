package dev.viren.coach.support;

import dev.viren.coach.config.CoachProperties;
import dev.viren.coach.config.CoachProperties.Ai;
import dev.viren.coach.config.CoachProperties.Difficulty;
import dev.viren.coach.config.CoachProperties.Goals;
import dev.viren.coach.config.CoachProperties.Nudge;
import dev.viren.coach.config.CoachProperties.Outbox;
import dev.viren.coach.config.CoachProperties.Push;
import dev.viren.coach.config.CoachProperties.Queue;
import dev.viren.coach.config.CoachProperties.Recap;
import dev.viren.coach.config.CoachProperties.Sweeper;
import java.time.DayOfWeek;

/** The same values as application.yml, for plain unit tests that don't start Spring. */
public final class TestProperties {
    private TestProperties() {}

    public static CoachProperties defaults() {
        return new CoachProperties(
                new Goals(1),
                new Queue("in-memory", 100, 1, 3, 10),
                new Outbox(50, 100),
                new Nudge(1000, 100, 360, 1000, 3, 3, 5, 5, 120),
                new Difficulty(2, 1, 5, 3, 2),
                new Recap("-", DayOfWeek.SUNDAY, 18),
                new Sweeper(1000, 120000, 5),
                new Ai("template", "claude-opus-5-5", "claude-haiku-5-5", 16000, 120, ""),
                new Push("log", new Push.Fcm("", "")));
    }
}
