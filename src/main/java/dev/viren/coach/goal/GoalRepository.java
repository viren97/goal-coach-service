package dev.viren.coach.goal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, String> {
    Optional<Goal> findByIdempotencyKey(String idempotencyKey);

    long countByUserIdAndStatusIn(String userId, Collection<GoalStatus> statuses);

    List<Goal> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Goal> findTop100ByStatusAndNextNudgeAtLessThanEqualOrderByNextNudgeAtAsc(GoalStatus status, Instant now);

    List<Goal> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(GoalStatus status, Instant cutoff);

    List<Goal> findByStatus(GoalStatus status);
}
