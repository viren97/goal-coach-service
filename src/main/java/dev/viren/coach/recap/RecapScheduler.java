package dev.viren.coach.recap;

import dev.viren.coach.command.Commands.GoalCommand;
import dev.viren.coach.config.CoachProperties;
import dev.viren.coach.goal.Goal;
import dev.viren.coach.goal.GoalRepository;
import dev.viren.coach.goal.GoalStatus;
import dev.viren.coach.outbox.OutboxEventType;
import dev.viren.coach.outbox.OutboxService;
import dev.viren.coach.user.User;
import dev.viren.coach.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs hourly; a goal gets its recap when its owner's local time is the configured day and hour.
 * Claiming (lastRecapAt + GENERATE_RECAP outbox row) is one transaction, so each week is requested once.
 */
@Component
public class RecapScheduler {
    private final GoalRepository goals;
    private final UserRepository users;
    private final Claimer claimer;
    private final CoachProperties.Recap config;
    private final Clock clock;

    public RecapScheduler(GoalRepository goals, UserRepository users, Claimer claimer, CoachProperties properties, Clock clock) {
        this.goals = goals;
        this.users = users;
        this.claimer = claimer;
        this.config = properties.recap();
        this.clock = clock;
    }

    @Scheduled(cron = "${coach.recap.cron}")
    public int run() {
        Instant now = clock.instant();
        int requested = 0;
        for (Goal goal : goals.findByStatus(GoalStatus.ACTIVE)) {
            User user = users.findById(goal.getUserId()).orElse(null);
            if (user == null || !isRecapHour(now.atZone(user.zone()))) continue;
            try {
                if (claimer.claim(goal.getId())) requested++;
            } catch (OptimisticLockingFailureException ignored) {
                // another instance claimed it
            }
        }
        return requested;
    }

    boolean isRecapHour(ZonedDateTime local) {
        return local.getDayOfWeek() == config.dayOfWeek() && local.getHour() == config.hour();
    }

    @Component
    public static class Claimer {
        private final GoalRepository goals;
        private final OutboxService outbox;
        private final Clock clock;

        Claimer(GoalRepository goals, OutboxService outbox, Clock clock) {
            this.goals = goals;
            this.outbox = outbox;
            this.clock = clock;
        }

        @Transactional
        public boolean claim(String goalId) {
            Instant now = clock.instant();
            Goal goal = goals.findById(goalId).orElse(null);
            if (goal == null || !goal.isActive()) return false;
            if (goal.getLastRecapAt() != null && goal.getLastRecapAt().isAfter(now.minus(Duration.ofDays(6)))) return false;
            goal.markRecapRequested(now);
            outbox.record(OutboxEventType.GENERATE_RECAP, goalId, new GoalCommand(goalId));
            return true;
        }
    }
}
