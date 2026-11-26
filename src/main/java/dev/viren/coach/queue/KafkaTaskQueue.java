package dev.viren.coach.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Durable {@link TaskQueue} on Kafka ({@code coach.queue.type=kafka}, enabled in the docker profile).
 *
 * <ul>
 *   <li>Keyed by goal id, so all messages for one goal land on one partition, in order.</li>
 *   <li>publish() waits for the broker ack (acks=all, idempotent producer). The outbox row is marked published only
 *       after that, so a crash can cause a re-send but never a loss: at-least-once, consumers are idempotent.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "coach.queue", name = "type", havingValue = "kafka")
public class KafkaTaskQueue implements TaskQueue {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final long sendTimeoutMs;

    public KafkaTaskQueue(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper,
                          @Value("${coach.queue.kafka.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Override
    public void publish(QueueMessage message) {
        try {
            kafka.send(message.topic(), message.key(), objectMapper.writeValueAsString(message))
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka did not accept " + message.topic() + "/" + message.key() + ": " + e.getMessage(), e);
        }
    }
}
