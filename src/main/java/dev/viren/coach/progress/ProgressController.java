package dev.viren.coach.progress;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgressController {
    private final ProgressService progress;

    public ProgressController(ProgressService progress) {
        this.progress = progress;
    }

    @GetMapping("/api/v1/goals/{goalId}/progress")
    public ProgressView get(@PathVariable String goalId) {
        return progress.get(goalId);
    }
}
