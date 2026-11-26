package dev.viren.coach.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Routes Kafka records to the single {@link MessageConsumer} registered for each topic. */
@Component
@ConditionalOnProperty(prefix = "coach.queue", name = "type", havingValue = "kafka")
public class KafkaQueueListener {
    private final Map<String, MessageConsumer> consumerByTopic;
    private final ObjectMapper objectMapper;

    public KafkaQueueListener(List<MessageConsumer> consumers, ObjectMapper objectMapper) {
        this.consumerByTopic = consumers.stream().collect(Collectors.toMap(MessageConsumer::topic, Function.identity()));
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {Topics.COMMANDS, Topics.EVENTS},
            groupId = "${coach.queue.kafka.group-id:coach}",
            concurrency = "${coach.queue.workers-per-topic:4}")
    public void onMessage(ConsumerRecord<String, String> record) throws Exception {
        QueueMessage message = objectMapper.readValue(record.value(), QueueMessage.class);
        MessageConsumer consumer = consumerByTopic.get(record.topic());
        if (consumer == null) throw new IllegalStateException("No consumer registered for topic " + record.topic());
        consumer.consume(message);
    }
}
