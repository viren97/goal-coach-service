package dev.viren.coach.notification;

import dev.viren.coach.event.CoachEvent;
import dev.viren.coach.event.EventSubscriber;
import dev.viren.coach.push.PushMessage;
import dev.viren.coach.push.PushService;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pushes for the moments that matter besides tasks: milestone reached, goal completed, weekly recap, and the
 * fatigue check-in (with Easier / Keep / Pause buttons). Task nudges are sent by SendNudgeHandler.
 */
@Component
public class CoachNotificationSubscriber implements EventSubscriber {
    private final PushService push;

    public CoachNotificationSubscriber(PushService push) {
        this.push = push;
    }

    @Override
    public String name() {
        return "coach-notifications";
    }

    @Override
    public void onEvent(CoachEvent e) {
        PushMessage message = switch (e.type()) {
            case MILESTONE_COMPLETED -> info(e, "Milestone reached: " + e.title(), "Next one is unlocked. Keep the momentum.", "milestone");
            case GOAL_COMPLETED -> info(e, "You did it", "Every milestone done. You're living as " + e.title() + ".", "goal");
            case RECAP_READY -> info(e, e.title(), e.message(), "recap");
            case FATIGUE_CHECK_IN -> new PushMessage(e.title(), e.message(), PushMessage.CATEGORY_CHECK_IN,
                    "checkin-" + e.goalId(), Map.of("goalId", e.goalId(), "deepLink", "coach://goals/" + e.goalId() + "/check-in"));
            default -> null;
        };
        if (message != null) push.sendToUser(e.userId(), message);
    }

    private static PushMessage info(CoachEvent e, String title, String body, String kind) {
        return new PushMessage(title, body, PushMessage.CATEGORY_INFO, kind + "-" + e.goalId() + "-" + e.occurredAt().toEpochMilli(),
                Map.of("goalId", e.goalId(), "deepLink", "coach://goals/" + e.goalId()));
    }
}
