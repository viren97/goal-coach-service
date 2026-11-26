package dev.viren.coach.task;

import dev.viren.coach.config.CoachProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** SENT tasks with no action after {@code ignore-after-minutes} become EXPIRED (= ignored). */
@Component
public class TaskExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(TaskExpiryJob.class);

    private final TaskRepository tasks;
    private final TaskWorkflow workflow;
    private final Duration ignoreAfter;
    private final Clock clock;

    public TaskExpiryJob(TaskRepository tasks, TaskWorkflow workflow, CoachProperties properties, Clock clock) {
        this.tasks = tasks;
        this.workflow = workflow;
        this.ignoreAfter = Duration.ofMinutes(properties.nudge().ignoreAfterMinutes());
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${coach.nudge.expiry-interval-ms}", initialDelayString = "${coach.nudge.expiry-interval-ms}")
    public int run() {
        Instant cutoff = clock.instant().minus(ignoreAfter);
        int expired = 0;
        for (CoachTask task : tasks.findTop100ByStatusAndSentAtBeforeOrderBySentAtAsc(TaskStatus.SENT, cutoff)) {
            try {
                if (workflow.expire(task.getId())) expired++;
            } catch (OptimisticLockingFailureException e) {
                log.debug("Task {} changed concurrently, retrying next run", task.getId());
            }
        }
        if (expired > 0) log.info("Expired {} ignored task(s)", expired);
        return expired;
    }
}
