package dev.viren.coach.goal;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.viren.coach.ai.AiCoach;
import dev.viren.coach.ai.AiModels.GeneratedPlan;
import dev.viren.coach.ai.AiModels.PlanRequest;
import dev.viren.coach.command.CommandHandler;
import dev.viren.coach.command.Commands.GoalCommand;
import dev.viren.coach.outbox.OutboxEventType;
import dev.viren.coach.user.User;
import dev.viren.coach.user.UserRepository;
import org.springframework.stereotype.Component;

/** GENERATE_PLAN: call the AI (no transaction held), then apply the plan atomically. */
@Component
public class PlanGenerationHandler implements CommandHandler {
    private final GoalRepository goals;
    private final UserRepository users;
    private final AiCoach ai;
    private final PlanWriter writer;
    private final ObjectMapper objectMapper;

    public PlanGenerationHandler(GoalRepository goals, UserRepository users, AiCoach ai, PlanWriter writer, ObjectMapper objectMapper) {
        this.goals = goals;
        this.users = users;
        this.ai = ai;
        this.writer = writer;
        this.objectMapper = objectMapper;
    }

    @Override
    public OutboxEventType type() {
        return OutboxEventType.GENERATE_PLAN;
    }

    @Override
    public void handle(String payload) throws Exception {
        GoalCommand command = objectMapper.readValue(payload, GoalCommand.class);
        Goal goal = goals.findById(command.goalId()).orElse(null);
        if (goal == null || goal.getStatus() != GoalStatus.PLANNING) return; // duplicate or already planned

        User user = users.findById(goal.getUserId()).orElseThrow();
        GeneratedPlan plan = ai.generatePlan(new PlanRequest(goal.getIdentity(), goal.getContext(),
                user.getIntensity(), goal.getCurrentDifficulty()));
        writer.apply(goal.getId(), plan);
    }
}
