package dev.viren.coach.command;

import dev.viren.coach.outbox.OutboxEventType;
import dev.viren.coach.queue.MessageConsumer;
import dev.viren.coach.queue.QueueMessage;
import dev.viren.coach.queue.Topics;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The single consumer of {@code coach.commands}; routes each message to the handler registered for its type. */
@Component
public class CommandDispatcher implements MessageConsumer {
    private final Map<OutboxEventType, CommandHandler> handlers = new EnumMap<>(OutboxEventType.class);

    public CommandDispatcher(List<CommandHandler> handlers) {
        for (CommandHandler handler : handlers) {
            if (this.handlers.put(handler.type(), handler) != null) {
                throw new IllegalStateException("Two command handlers registered for " + handler.type());
            }
        }
    }

    @Override
    public String topic() {
        return Topics.COMMANDS;
    }

    @Override
    public void consume(QueueMessage message) {
        CommandHandler handler = handlers.get(OutboxEventType.valueOf(message.type()));
        if (handler == null) throw new IllegalStateException("No handler for command " + message.type());
        try {
            handler.handle(message.payload());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
