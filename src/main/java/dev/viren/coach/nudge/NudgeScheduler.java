package dev.viren.coach.nudge;

import dev.viren.coach.goal.Goal;
import dev.viren.coach.goal.GoalRepository;
import dev.viren.coach.goal.GoalStatus;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finds goals whose {@code nextNudgeAt} has passed (indexed query, so cost scales with due goals, not all goals)
 * and schedules one nudge each. Safe on several instances: the goal row's version lets only one commit.
 */
@Component
public class NudgeScheduler {
    private static final Logger log = LoggerFactory.getLogger(NudgeScheduler.class);

    private final GoalRepository goals;
    private final NudgeWriter writer;
    private final Clock clock;

    public NudgeScheduler(GoalRepository goals, NudgeWriter writer, Clock clock) {
        this.goals = goals;
        this.writer = writer;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${coach.nudge.scheduler-interval-ms}", initialDelayString = "${coach.nudge.scheduler-interval-ms}")
    public int tick() {
        int scheduled = 0;
        for (Goal goal : goals.findTop100ByStatusAndNextNudgeAtLessThanEqualOrderByNextNudgeAtAsc(GoalStatus.ACTIVE, clock.instant())) {
            try {
                if (writer.scheduleDueNudge(goal.getId())) scheduled++;
            } catch (OptimisticLockingFailureException e) {
                log.debug("Goal {} changed concurrently, skipping this tick", goal.getId());
            } catch (Exception e) {
                log.warn("Could not schedule nudge for goal {}: {}", goal.getId(), e.toString());
            }
        }
        if (scheduled > 0) log.info("Scheduled {} nudge(s)", scheduled);
        return scheduled;
    }
}
