package dev.viren.coach.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    public record ActionRequest(
            @NotNull TaskAction action,
            @Size(max = 1000) String note,
            @Size(max = 1000) @Pattern(regexp = "^https://.*", message = "must be an https URL") String proofUrl,
            @Min(5) @Max(10080) Long snoozeMinutes) {}

    public record TaskResponse(String id, String goalId, String milestoneId, TaskKind kind, String title, String body,
                               String resourceUrl, int difficulty, int estMinutes, TaskStatus status, Instant notBefore,
                               Instant sentAt, Instant actedAt, int snoozeCount, String note, String proofUrl) {
        public static TaskResponse from(CoachTask t) {
            return new TaskResponse(t.getId(), t.getGoalId(), t.getMilestoneId(), t.getKind(), t.getTitle(), t.getBody(),
                    t.getResourceUrl(), t.getDifficulty(), t.getEstMinutes(), t.getStatus(), t.getNotBefore(),
                    t.getSentAt(), t.getActedAt(), t.getSnoozeCount(), t.getNote(), t.getProofUrl());
        }
    }

    /** Called by the app and by notification action buttons. 200 for both a new action and a replay. */
    @PostMapping("/tasks/{id}/actions")
    public TaskResponse act(@PathVariable String id, @Valid @RequestBody ActionRequest r) {
        return TaskResponse.from(taskService.act(id, r.action(), r.note(), r.proofUrl(), r.snoozeMinutes()).task());
    }

    @GetMapping("/tasks/{id}")
    public TaskResponse get(@PathVariable String id) {
        return TaskResponse.from(taskService.get(id));
    }

    @GetMapping("/goals/{goalId}/tasks")
    public List<TaskResponse> forGoal(@PathVariable String goalId, @RequestParam(required = false) TaskStatus status) {
        return taskService.forGoal(goalId, status).stream().map(TaskResponse::from).toList();
    }

    @GetMapping("/goals/{goalId}/today")
    public ResponseEntity<TaskResponse> today(@PathVariable String goalId) {
        return taskService.today(goalId).map(t -> ResponseEntity.ok(TaskResponse.from(t)))
                .orElse(ResponseEntity.noContent().build());
    }
}
