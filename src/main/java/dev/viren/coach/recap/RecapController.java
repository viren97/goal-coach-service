package dev.viren.coach.recap;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecapController {
    private final RecapRepository recaps;

    public RecapController(RecapRepository recaps) {
        this.recaps = recaps;
    }

    public record RecapResponse(String id, LocalDate weekStart, String message, List<String> highlights,
                                int tasksDone, int difficultyChange, Instant createdAt) {
        static RecapResponse from(Recap r) {
            List<String> highlights = r.getHighlights() == null || r.getHighlights().isBlank()
                    ? List.of() : Arrays.asList(r.getHighlights().split("\n"));
            return new RecapResponse(r.getId(), r.getWeekStart(), r.getMessage(), highlights, r.getTasksDone(),
                    r.getDifficultyChange(), r.getCreatedAt());
        }
    }

    @GetMapping("/api/v1/goals/{goalId}/recaps")
    @Transactional(readOnly = true)
    public List<RecapResponse> list(@PathVariable String goalId) {
        return recaps.findByGoalIdOrderByWeekStartDesc(goalId).stream().map(RecapResponse::from).toList();
    }
}
