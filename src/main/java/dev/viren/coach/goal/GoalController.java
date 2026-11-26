package dev.viren.coach.goal;

import dev.viren.coach.goal.GoalService.CheckInAnswer;
import dev.viren.coach.goal.GoalService.Submission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GoalController {
    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    public record CreateGoalRequest(
            @NotBlank @Size(max = 300) String identity,
            @Size(max = 2000) String context) {}

    public record CheckInRequest(@NotNull CheckInAnswer answer) {}

    public record MilestoneResponse(String id, int position, String title, String description, int targetDone,
                                    MilestoneStatus status, Instant completedAt) {
        static MilestoneResponse from(Milestone m) {
            return new MilestoneResponse(m.getId(), m.getPosition(), m.getTitle(), m.getDescription(), m.getTargetDone(),
                    m.getStatus(), m.getCompletedAt());
        }
    }

    public record GoalResponse(String id, String userId, String identity, String context, GoalStatus status,
                               String planSummary, int currentDifficulty, int ignoredStreak, Instant nextNudgeAt,
                               Instant createdAt, Instant completedAt, List<MilestoneResponse> milestones) {
        static GoalResponse from(Goal g, List<Milestone> milestones) {
            return new GoalResponse(g.getId(), g.getUserId(), g.getIdentity(), g.getContext(), g.getStatus(),
                    g.getPlanSummary(), g.getCurrentDifficulty(), g.getIgnoredStreak(), g.getNextNudgeAt(),
                    g.getCreatedAt(), g.getCompletedAt(), milestones.stream().map(MilestoneResponse::from).toList());
        }
    }

    /** 202 for a new goal (plan generated in the background), 200 for an idempotent replay. */
    @PostMapping("/users/{userId}/goals")
    public ResponseEntity<GoalResponse> create(
            @PathVariable String userId,
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 100) String idempotencyKey,
            @Valid @RequestBody CreateGoalRequest r) {
        Submission s = goalService.create(userId, idempotencyKey, r.identity(), r.context());
        return ResponseEntity.status(s.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(view(s.goal()));
    }

    @GetMapping("/users/{userId}/goals")
    public List<GoalResponse> forUser(@PathVariable String userId) {
        return goalService.forUser(userId).stream().map(this::view).toList();
    }

    @GetMapping("/goals/{id}")
    public GoalResponse get(@PathVariable String id) {
        return view(goalService.get(id));
    }

    @PostMapping("/goals/{id}/pause")
    public GoalResponse pause(@PathVariable String id) {
        return view(goalService.pause(id));
    }

    @PostMapping("/goals/{id}/resume")
    public GoalResponse resume(@PathVariable String id) {
        return view(goalService.resume(id));
    }

    @PostMapping("/goals/{id}/check-in")
    public GoalResponse checkIn(@PathVariable String id, @Valid @RequestBody CheckInRequest r) {
        return view(goalService.checkIn(id, r.answer()));
    }

    private GoalResponse view(Goal goal) {
        return GoalResponse.from(goal, goalService.milestones(goal.getId()));
    }
}
