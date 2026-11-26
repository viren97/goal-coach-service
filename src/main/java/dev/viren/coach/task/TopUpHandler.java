package dev.viren.coach.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.viren.coach.ai.AiCoach;
import dev.viren.coach.ai.AiModels.GeneratedTask;
import dev.viren.coach.ai.AiModels.GeneratedTaskBatch;
import dev.viren.coach.ai.AiModels.TaskBatchRequest;
import dev.viren.coach.command.CommandHandler;
import dev.viren.coach.command.Commands.TopUpCommand;
import dev.viren.coach.config.CoachProperties;
import dev.viren.coach.goal.Goal;
import dev.viren.coach.goal.GoalRepository;
import dev.viren.coach.goal.Milestone;
import dev.viren.coach.goal.MilestoneRepository;
import dev.viren.coach.goal.MilestoneStatus;
import dev.viren.coach.outbox.OutboxEventType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TOP_UP_TASKS: when the active milestone's pool runs low (or a task was swapped), ask the AI for a small batch.
 * The AI call runs outside any transaction; the insert re-checks that the milestone is still active.
 * Idempotent enough: a duplicate command sees a full pool and does nothing.
 */
@Component
public class TopUpHandler implements CommandHandler {
    private final GoalRepository goals;
    private final MilestoneRepository milestones;
    private final TaskRepository tasks;
    private final AiCoach ai;
    private final Writer writer;
    private final ObjectMapper objectMapper;
    private final CoachProperties.Nudge config;

    public TopUpHandler(GoalRepository goals, MilestoneRepository milestones, TaskRepository tasks, AiCoach ai,
                        Writer writer, ObjectMapper objectMapper, CoachProperties properties) {
        this.goals = goals;
        this.milestones = milestones;
        this.tasks = tasks;
        this.ai = ai;
        this.writer = writer;
        this.objectMapper = objectMapper;
        this.config = properties.nudge();
    }

    @Override
    public OutboxEventType type() {
        return OutboxEventType.TOP_UP_TASKS;
    }

    @Override
    public void handle(String payload) throws Exception {
        TopUpCommand command = objectMapper.readValue(payload, TopUpCommand.class);
        Goal goal = goals.findById(command.goalId()).orElse(null);
        if (goal == null || !goal.isActive()) return;
        Optional<Milestone> active = milestones.findFirstByGoalIdAndStatus(goal.getId(), MilestoneStatus.ACTIVE);
        if (active.isEmpty()) return;
        Milestone milestone = active.get();
        if (tasks.countByMilestoneIdAndStatus(milestone.getId(), TaskStatus.QUEUED) >= config.minPoolSize()) return;

        List<String> recent = tasks.findTop20ByGoalIdOrderByCreatedAtDesc(goal.getId()).stream().map(CoachTask::getTitle).toList();
        GeneratedTaskBatch batch = ai.generateTasks(new TaskBatchRequest(goal.getIdentity(), milestone.getTitle(),
                milestone.getDescription(), goal.getCurrentDifficulty(), config.topUpBatchSize(), recent, command.reason()));
        writer.addTasks(goal, milestone.getId(), batch.tasks());
    }

    @Component
    static class Writer {
        private final MilestoneRepository milestones;
        private final TaskRepository tasks;
        private final Clock clock;

        Writer(MilestoneRepository milestones, TaskRepository tasks, Clock clock) {
            this.milestones = milestones;
            this.tasks = tasks;
            this.clock = clock;
        }

        @Transactional
        public void addTasks(Goal goal, String milestoneId, List<GeneratedTask> generated) {
            Milestone milestone = milestones.findById(milestoneId).orElseThrow();
            if (milestone.getStatus() != MilestoneStatus.ACTIVE) return;
            Instant now = clock.instant();
            int position = tasks.maxPosition(milestoneId);
            for (GeneratedTask g : generated) {
                tasks.save(new CoachTask(goal.getId(), milestoneId, goal.getUserId(), g.kind(), g.title(), g.body(),
                        g.resourceUrl(), g.difficulty(), g.estMinutes(), ++position, TaskOrigin.TOP_UP, now));
            }
        }
    }
}
