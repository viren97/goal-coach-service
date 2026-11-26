package dev.viren.coach.goal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, String> {
    List<Milestone> findByGoalIdOrderByPositionAsc(String goalId);

    Optional<Milestone> findFirstByGoalIdAndStatus(String goalId, MilestoneStatus status);

    Optional<Milestone> findByGoalIdAndPosition(String goalId, int position);
}
