package dev.viren.coach.recovery;

import dev.viren.coach.config.CoachProperties;
import dev.viren.coach.goal.Goal;
import dev.viren.coach.goal.GoalRepository;
import dev.viren.coach.goal.GoalStatus;
import dev.viren.coach.goal.PlanWriter;
import dev.viren.coach.nudge.NudgeWriter;
import dev.viren.coach.task.CoachTask;
import dev.viren.coach.task.TaskRepository;
import dev.viren.coach.task.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net for at-least-once processing. Work gets stranded when its queue message is lost (in-memory queue on
 * restart, dead-lettered message) or a worker crashes mid-way:
 * <ul>
 *   <li>goal stuck in PLANNING → GENERATE_PLAN re-queued (PLAN_FAILED after max attempts)</li>
 *   <li>task stuck in SCHEDULED → back to QUEUED and the goal is due again</li>
 * </ul>
 * Safe on several instances and against a slow-but-alive worker: every write re-checks status under @Version.
 */
@Component
public class StuckWorkSweeper {
    private static final Logger log = LoggerFactory.getLogger(StuckWorkSweeper.class);

    private final GoalRepository goals;
    private final TaskRepository tasks;
    private final PlanWriter planWriter;
    private final NudgeWriter nudgeWriter;
    private final long staleAfterMs;
    private final Clock clock;

    public StuckWorkSweeper(GoalRepository goals, TaskRepository tasks, PlanWriter planWriter, NudgeWriter nudgeWriter,
                            CoachProperties properties, Clock clock) {
        this.goals = goals;
        this.tasks = tasks;
        this.planWriter = planWriter;
        this.nudgeWriter = nudgeWriter;
        this.staleAfterMs = properties.sweeper().staleAfterMs();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${coach.sweeper.interval-ms}", initialDelayString = "${coach.sweeper.interval-ms}")
    public int sweep() {
        Instant cutoff = clock.instant().minusMillis(staleAfterMs);
        int recovered = 0;
        for (Goal goal : goals.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(GoalStatus.PLANNING, cutoff)) {
            try {
                if (planWriter.retry(goal.getId())) recovered++;
            } catch (Exception e) {
                log.warn("Sweeper could not retry plan for goal {}: {}", goal.getId(), e.toString());
            }
        }
        for (CoachTask task : tasks.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(TaskStatus.SCHEDULED, cutoff)) {
            try {
                if (nudgeWriter.requeueStuck(task.getId())) recovered++;
            } catch (Exception e) {
                log.warn("Sweeper could not requeue task {}: {}", task.getId(), e.toString());
            }
        }
        if (recovered > 0) log.warn("Sweeper recovered {} stranded item(s)", recovered);
        return recovered;
    }
}
