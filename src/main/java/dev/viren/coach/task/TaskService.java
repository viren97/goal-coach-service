package dev.viren.coach.task;

import dev.viren.coach.common.error.CoachException;
import dev.viren.coach.progress.ProgressCache;
import dev.viren.coach.task.TaskWorkflow.ActionResult;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private static final int MAX_RETRIES = 3;

    private final TaskWorkflow workflow;
    private final TaskRepository tasks;
    private final ProgressCache progressCache;

    public TaskService(TaskWorkflow workflow, TaskRepository tasks, ProgressCache progressCache) {
        this.workflow = workflow;
        this.tasks = tasks;
        this.progressCache = progressCache;
    }

    /**
     * The action and the scheduler can touch the same goal row at once. Retry the whole transaction a few times
     * (each attempt re-reads fresh versions) before surfacing a 409 to the client.
     */
    public ActionResult act(String taskId, TaskAction action, String note, String proofUrl, Long snoozeMinutes) {
        int attempt = 0;
        while (true) {
            try {
                ActionResult result = workflow.act(taskId, action, note, proofUrl, snoozeMinutes);
                progressCache.evict(result.task().getGoalId()); // after commit: the next read is fresh
                return result;
            } catch (OptimisticLockingFailureException e) {
                if (++attempt >= MAX_RETRIES) throw e;
                sleep(20L * attempt);
            }
        }
    }

    @Transactional(readOnly = true)
    public CoachTask get(String taskId) {
        return tasks.findById(taskId).orElseThrow(() -> CoachException.notFound("Task", taskId));
    }

    @Transactional(readOnly = true)
    public List<CoachTask> forGoal(String goalId, TaskStatus status) {
        return status == null ? tasks.findByGoalIdOrderByPositionAsc(goalId)
                : tasks.findByGoalIdAndStatusOrderByPositionAsc(goalId, status);
    }

    /** "Today's task": the most recently sent task that is still open. */
    @Transactional(readOnly = true)
    public Optional<CoachTask> today(String goalId) {
        return tasks.findFirstByGoalIdAndStatusOrderBySentAtDesc(goalId, TaskStatus.SENT);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
