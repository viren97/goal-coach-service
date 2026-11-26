package dev.viren.coach.recap;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecapRepository extends JpaRepository<Recap, String> {
    boolean existsByGoalIdAndWeekStart(String goalId, LocalDate weekStart);

    List<Recap> findByGoalIdOrderByWeekStartDesc(String goalId);
}
