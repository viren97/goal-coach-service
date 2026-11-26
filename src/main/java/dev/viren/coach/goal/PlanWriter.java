package dev.viren.coach.goal;

import dev.viren.coach.ai.AiModels.GeneratedMilestone;
import dev.viren.coach.ai.AiModels.GeneratedPlan;
import dev.viren.coach.ai.AiModels.GeneratedTask;
import dev.viren.coach.command.Commands.GoalCommand;
import dev.viren.coach.config.CoachProperties;
import dev.viren.coach.event.CoachEvent;
import dev.viren.coach.event.CoachEventPublisher;
import dev.viren.coach.event.CoachEventType;
import dev.viren.coach.outbox.OutboxEventType;
import dev.viren.coach.outbox.OutboxService;
import dev.viren.coach.task.CoachTask;
import dev.viren.coach.task.TaskOrigin;
import dev.viren.coach.task.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlanWriter {
    private static final Logger log = LoggerFactory.getLogger(PlanWriter.class);

    private final GoalRepository goals;
    private final MilestoneRepository milestones;
    private final TaskRepository tasks;
    private final OutboxService outbox;
    private final CoachEventPublisher events;
    private final CoachProperties.Sweeper sweeper;
    private final Clock clock;

    public PlanWriter(GoalRepository goals, MilestoneRepository milestones, TaskRepository tasks, OutboxService outbox,
                      CoachEventPublisher events, CoachProperties properties, Clock clock) {
        this.goals = goals;
        this.milestones = milestones;
        this.tasks = tasks;
        this.outbox = outbox;
        this.events = events;
        this.sweeper = properties.sweeper();
        this.clock = clock;
    }

    /**
     * Saves milestones + every task of the plan and activates the goal, in one transaction. Only applies while the
     * goal is still PLANNING, so a duplicate GENERATE_PLAN (redelivery, sweeper retry) can't create a second plan;
     * if two race, the goal's @Version lets one commit.
     */
    @Transactional
    public boolean apply(String goalId, GeneratedPlan plan) {
        Goal goal = goals.findById(goalId).orElse(null);
        if (goal == null || goal.getStatus() != GoalStatus.PLANNING) return false;
        Instant now = clock.instant();

        int position = 0;
        for (int m = 0; m < plan.milestones().size(); m++) {
            GeneratedMilestone gm = plan.milestones().get(m);
            Milestone milestone = milestones.save(new Milestone(goalId, m + 1, gm.title(), gm.description(),
                    gm.tasks().size(), m == 0 ? MilestoneStatus.ACTIVE : MilestoneStatus.LOCKED));
            for (GeneratedTask t : gm.tasks()) {
                tasks.save(new CoachTask(goalId, milestone.getId(), goal.getUserId(), t.kind(), t.title(), t.body(),
                        t.resourceUrl(), t.difficulty(), t.estMinutes(), ++position, TaskOrigin.PLAN, now));
            }
        }
        goal.activate(plan.summary(), now, now); // first nudge straight after onboarding
        events.publish(new CoachEvent(CoachEventType.PLAN_READY, goalId, goal.getUserId(), null,
                goal.getIdentity(), plan.summary(), now));
        return true;
    }

    /** Sweeper path: plan generation never finished. Re-queue it, or give up after max attempts. */
    @Transactional
    public boolean retry(String goalId) {
        Goal goal = goals.findById(goalId).orElse(null);
        if (goal == null || goal.getStatus() != GoalStatus.PLANNING) return false;
        Instant now = clock.instant();
        if (goal.getPlanAttempts() >= sweeper.maxPlanAttempts()) {
            log.error("Plan generation for goal {} failed {} times, giving up", goalId, goal.getPlanAttempts());
            goal.failPlanning(now);
            return false;
        }
        goal.retryPlanning(now);
        outbox.record(OutboxEventType.GENERATE_PLAN, goalId, new GoalCommand(goalId));
        return true;
    }
}
