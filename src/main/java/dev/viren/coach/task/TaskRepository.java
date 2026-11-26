package dev.viren.coach.task;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<CoachTask, String> {
    List<CoachTask> findByMilestoneIdAndStatus(String milestoneId, TaskStatus status);

    long countByMilestoneIdAndStatus(String milestoneId, TaskStatus status);

    List<CoachTask> findByGoalIdOrderByPositionAsc(String goalId);

    List<CoachTask> findByGoalIdAndStatusOrderByPositionAsc(String goalId, TaskStatus status);

    List<CoachTask> findByGoalIdAndStatusIn(String goalId, Collection<TaskStatus> statuses);

    List<CoachTask> findTop20ByGoalIdOrderByCreatedAtDesc(String goalId);

    Optional<CoachTask> findFirstByGoalIdAndStatusOrderBySentAtDesc(String goalId, TaskStatus status);

    List<CoachTask> findTop100ByStatusAndSentAtBeforeOrderBySentAtAsc(TaskStatus status, Instant cutoff);

    List<CoachTask> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(TaskStatus status, Instant cutoff);

    List<CoachTask> findTop30ByUserIdAndStatusAndPushedTrueOrderByActedAtDesc(String userId, TaskStatus status);

    List<CoachTask> findByGoalIdAndUpdatedAtAfter(String goalId, Instant since);

    @Query("select coalesce(max(t.position), 0) from CoachTask t where t.milestoneId = ?1")
    int maxPosition(String milestoneId);
}
