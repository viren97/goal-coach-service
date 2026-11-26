package dev.viren.coach.recap;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.viren.coach.ai.AiCoach;
import dev.viren.coach.ai.AiModels.GeneratedRecap;
import dev.viren.coach.ai.AiModels.RecapRequest;
import dev.viren.coach.command.CommandHandler;
import dev.viren.coach.command.Commands.GoalCommand;
import dev.viren.coach.event.CoachEvent;
import dev.viren.coach.event.CoachEventPublisher;
import dev.viren.coach.event.CoachEventType;
import dev.viren.coach.goal.Goal;
import dev.viren.coach.goal.GoalRepository;
import dev.viren.coach.outbox.OutboxEventType;
import dev.viren.coach.progress.ProgressService;
import dev.viren.coach.task.CoachTask;
import dev.viren.coach.task.DifficultyPolicy;
import dev.viren.coach.task.TaskRepository;
import dev.viren.coach.task.TaskStatus;
import dev.viren.coach.user.User;
import dev.viren.coach.user.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** GENERATE_RECAP: summarise the last 7 days, let the AI write the message and nudge difficulty, push it. */
@Component
public class RecapHandler implements CommandHandler {
    private final GoalRepository goals;
    private final UserRepository users;
    private final TaskRepository tasks;
    private final RecapRepository recaps;
    private final ProgressService progress;
    private final AiCoach ai;
    private final Writer writer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RecapHandler(GoalRepository goals, UserRepository users, TaskRepository tasks, RecapRepository recaps,
                        ProgressService progress, AiCoach ai, Writer writer, ObjectMapper objectMapper, Clock clock) {
        this.goals = goals;
        this.users = users;
        this.tasks = tasks;
        this.recaps = recaps;
        this.progress = progress;
        this.ai = ai;
        this.writer = writer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public OutboxEventType type() {
        return OutboxEventType.GENERATE_RECAP;
    }

    @Override
    public void handle(String payload) throws Exception {
        GoalCommand command = objectMapper.readValue(payload, GoalCommand.class);
        Goal goal = goals.findById(command.goalId()).orElse(null);
        if (goal == null || !goal.isActive()) return;
        User user = users.findById(goal.getUserId()).orElseThrow();
        Instant now = clock.instant();
        LocalDate weekStart = now.atZone(user.zone()).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (recaps.existsByGoalIdAndWeekStart(goal.getId(), weekStart)) return;

        List<CoachTask> week = tasks.findByGoalIdAndUpdatedAtAfter(goal.getId(), now.minus(Duration.ofDays(7)));
        List<String> doneTitles = week.stream().filter(t -> t.getStatus() == TaskStatus.DONE).map(CoachTask::getTitle).toList();
        RecapRequest request = new RecapRequest(goal.getIdentity(), doneTitles.size(),
                (int) week.stream().filter(t -> t.getSnoozeCount() > 0 && !t.getStatus().isTerminal()).count(),
                count(week, TaskStatus.SWAPPED), count(week, TaskStatus.EXPIRED), goal.getCurrentDifficulty(),
                progress.get(goal.getId()).currentStreakDays(), doneTitles);

        GeneratedRecap recap = ai.weeklyRecap(request);
        writer.save(goal.getId(), weekStart, doneTitles.size(), recap);
    }

    private static int count(List<CoachTask> tasks, TaskStatus status) {
        return (int) tasks.stream().filter(t -> t.getStatus() == status).count();
    }

    @Component
    static class Writer {
        private final GoalRepository goals;
        private final RecapRepository recaps;
        private final DifficultyPolicy difficulty;
        private final CoachEventPublisher events;
        private final Clock clock;

        Writer(GoalRepository goals, RecapRepository recaps, DifficultyPolicy difficulty, CoachEventPublisher events, Clock clock) {
            this.goals = goals;
            this.recaps = recaps;
            this.difficulty = difficulty;
            this.events = events;
            this.clock = clock;
        }

        @Transactional
        public void save(String goalId, LocalDate weekStart, int done, GeneratedRecap recap) {
            if (recaps.existsByGoalIdAndWeekStart(goalId, weekStart)) return;
            Goal goal = goals.findById(goalId).orElseThrow();
            Instant now = clock.instant();
            recaps.save(new Recap(goalId, weekStart, recap.message(), String.join("\n", recap.highlights()), done,
                    recap.difficultyChange()));
            goal.applyDifficulty(difficulty.shift(goal.difficultyState(), recap.difficultyChange()), now);
            events.publish(new CoachEvent(CoachEventType.RECAP_READY, goalId, goal.getUserId(), null,
                    "Your week as " + goal.getIdentity(), recap.message(), now));
        }
    }
}
