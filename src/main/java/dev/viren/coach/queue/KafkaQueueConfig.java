package dev.viren.coach.queue;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
@ConditionalOnProperty(prefix = "coach.queue", name = "type", havingValue = "kafka")
public class KafkaQueueConfig {

    @Bean
    public NewTopic commandsTopic(@Value("${coach.queue.kafka.partitions:6}") int partitions) {
        return TopicBuilder.name(Topics.COMMANDS).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic eventsTopic(@Value("${coach.queue.kafka.partitions:6}") int partitions) {
        return TopicBuilder.name(Topics.EVENTS).partitions(partitions).replicas(1).build();
    }

    /** Retries a failing record in place with exponential backoff, then parks it on {@code <topic>.DLT}. */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> template,
            @Value("${coach.queue.max-attempts:3}") int maxAttempts,
            @Value("${coach.queue.retry-backoff-ms:200}") long backoffMs) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(Math.max(0, maxAttempts - 1));
        backOff.setInitialInterval(backoffMs);
        backOff.setMultiplier(2.0);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
    }
}
