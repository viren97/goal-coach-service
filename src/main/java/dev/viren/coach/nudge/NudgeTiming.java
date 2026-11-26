package dev.viren.coach.nudge;

import dev.viren.coach.goal.Goal;
import dev.viren.coach.user.User;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Combines slots, fatigue and learned response time into "when should this goal nudge next". */
@Component
public class NudgeTiming {
    private final NudgeSlotPlanner planner;
    private final FatigueGuard fatigue;
    private final ResponseTimeLearner learner;

    public NudgeTiming(NudgeSlotPlanner planner, FatigueGuard fatigue, ResponseTimeLearner learner) {
        this.planner = planner;
        this.fatigue = fatigue;
        this.learner = learner;
    }

    public Instant next(Goal goal, User user, Instant after) {
        int perDay = fatigue.pushesPerDay(user.getIntensity(), goal.getIgnoredStreak());
        Integer preferred = learner.preferredHour(user.getId(), user.zone());
        return planner.nextSlot(user.zone(), user.getWindowStartHour(), user.getWindowEndHour(), perDay, preferred, after);
    }
}
