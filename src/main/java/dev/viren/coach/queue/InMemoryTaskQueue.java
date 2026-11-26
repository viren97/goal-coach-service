package dev.viren.coach.queue;

import dev.viren.coach.config.CoachProperties;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default {@link TaskQueue} for local runs and tests. Messages live in memory, so anything queued is lost on restart;
 * the outbox + StuckWorkSweeper recover that work. Use {@code coach.queue.type=kafka} for durability.
 *
 * <p>Bounded queue per topic with a worker pool, retry with linear backoff and a dead-letter list.
 * Delivery is at-least-once (the outbox may republish), so consumers must be idempotent.
 */
@Component
@ConditionalOnProperty(prefix = "coach.queue", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryTaskQueue implements TaskQueue, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(InMemoryTaskQueue.class);

    private final CoachProperties.Queue properties;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Map<String, MessageConsumer> consumerByTopic;
    private final Map<String, LinkedBlockingQueue<QueueMessage>> queues;
    private final ExecutorService workers;
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentLinkedQueue<QueueMessage> deadLetters = new ConcurrentLinkedQueue<>();

    public InMemoryTaskQueue(List<MessageConsumer> consumers, CoachProperties properties) {
        this.properties = properties.queue();
        this.consumerByTopic = consumers.stream().collect(Collectors.toMap(MessageConsumer::topic, Function.identity()));
        this.queues = consumerByTopic.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), t -> new LinkedBlockingQueue<>(this.properties.capacity())));
        this.workers = Executors.newFixedThreadPool(Math.max(1, queues.size() * this.properties.workersPerTopic()));
        queues.forEach((topic, queue) -> {
            for (int i = 0; i < this.properties.workersPerTopic(); i++) {
                workers.submit(() -> workerLoop(topic, queue));
            }
        });
    }

    @Override
    public void publish(QueueMessage message) {
        LinkedBlockingQueue<QueueMessage> queue = queues.get(message.topic());
        if (queue == null) throw new IllegalStateException("No consumer registered for topic " + message.topic());
        if (!queue.offer(message)) throw new IllegalStateException("Queue " + message.topic() + " is full");
    }

    public List<QueueMessage> deadLetters() {
        return List.copyOf(deadLetters);
    }

    private void workerLoop(String topic, LinkedBlockingQueue<QueueMessage> queue) {
        MessageConsumer consumer = consumerByTopic.get(topic);
        while (running.get()) {
            QueueMessage message;
            try {
                message = queue.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (message == null) continue;
            try {
                consumer.consume(message);
            } catch (Exception e) {
                handleFailure(message, e);
            }
        }
    }

    private void handleFailure(QueueMessage message, Exception error) {
        if (message.attempt() >= properties.maxAttempts()) {
            log.error("Message {} {} on {} dead-lettered after {} attempts",
                    message.type(), message.key(), message.topic(), message.attempt(), error);
            deadLetters.add(message);
            return;
        }
        log.warn("Message {} {} on {} failed (attempt {}), retrying: {}",
                message.type(), message.key(), message.topic(), message.attempt(), error.toString());
        retryScheduler.schedule(() -> publish(message.nextAttempt()),
                properties.retryBackoffMs() * message.attempt(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() throws InterruptedException {
        running.set(false);
        retryScheduler.shutdownNow();
        workers.shutdown();
        workers.awaitTermination(5, TimeUnit.SECONDS);
    }
}
