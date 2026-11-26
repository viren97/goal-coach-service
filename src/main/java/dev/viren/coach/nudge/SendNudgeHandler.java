package dev.viren.coach.nudge;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.viren.coach.command.CommandHandler;
import dev.viren.coach.command.Commands.TaskCommand;
import dev.viren.coach.outbox.OutboxEventType;
import dev.viren.coach.push.PushMessage;
import dev.viren.coach.push.PushService;
import dev.viren.coach.task.CoachTask;
import dev.viren.coach.task.TaskKind;
import dev.viren.coach.task.TaskRepository;
import dev.viren.coach.task.TaskStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * SEND_NUDGE: push the task with Done / Snooze / Swap buttons, then mark it SENT.
 *
 * <p>Push happens outside a DB transaction. If we crash after the push but before markSent, the retry pushes again
 * with the same collapse key (= task id), so the device still shows one notification. A task that is no longer
 * SCHEDULED (already sent, done from the app, swapped) is skipped: duplicates stop here.
 */
@Component
public class SendNudgeHandler implements CommandHandler {
    private final TaskRepository tasks;
    private final PushService push;
    private final NudgeWriter writer;
    private final ObjectMapper objectMapper;

    public SendNudgeHandler(TaskRepository tasks, PushService push, NudgeWriter writer, ObjectMapper objectMapper) {
        this.tasks = tasks;
        this.push = push;
        this.writer = writer;
        this.objectMapper = objectMapper;
    }

    @Override
    public OutboxEventType type() {
        return OutboxEventType.SEND_NUDGE;
    }

    @Override
    public void handle(String payload) throws Exception {
        TaskCommand command = objectMapper.readValue(payload, TaskCommand.class);
        CoachTask task = tasks.findById(command.taskId()).orElse(null);
        if (task == null || task.getStatus() != TaskStatus.SCHEDULED) return;

        int delivered = push.sendToUser(task.getUserId(), message(task));
        writer.markSent(task.getId(), delivered > 0);
    }

    static PushMessage message(CoachTask task) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("taskId", task.getId());
        data.put("goalId", task.getGoalId());
        data.put("kind", task.getKind().name());
        data.put("deepLink", "coach://tasks/" + task.getId());
        if (task.getResourceUrl() != null) data.put("resourceUrl", task.getResourceUrl());
        String prefix = task.getKind() == TaskKind.LEARN ? "Learn · " : "Do · ";
        String body = (task.getBody() == null ? "" : task.getBody() + " ") + "(" + task.getEstMinutes() + " min)";
        return new PushMessage(prefix + task.getTitle(), body.trim(), PushMessage.CATEGORY_TASK, "task-" + task.getId(), data);
    }
}
