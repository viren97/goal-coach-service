package dev.viren.coach.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.viren.coach.queue.MessageConsumer;
import dev.viren.coach.queue.QueueMessage;
import dev.viren.coach.queue.Topics;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Async fan-out (observer): one event, many subscribers, each isolated so one failure doesn't block the others. */
@Component
public class CoachEventFanOut implements MessageConsumer {
    private static final Logger log = LoggerFactory.getLogger(CoachEventFanOut.class);

    private final List<EventSubscriber> subscribers;
    private final ObjectMapper objectMapper;

    public CoachEventFanOut(List<EventSubscriber> subscribers, ObjectMapper objectMapper) {
        this.subscribers = subscribers;
        this.objectMapper = objectMapper;
    }

    @Override
    public String topic() {
        return Topics.EVENTS;
    }

    @Override
    public void consume(QueueMessage message) {
        CoachEvent event;
        try {
            event = objectMapper.readValue(message.payload(), CoachEvent.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable coach event", e);
        }
        for (EventSubscriber subscriber : subscribers) {
            try {
                subscriber.onEvent(event);
            } catch (Exception e) {
                log.error("Subscriber {} failed for {} on goal {}", subscriber.name(), event.type(), event.goalId(), e);
            }
        }
    }
}
