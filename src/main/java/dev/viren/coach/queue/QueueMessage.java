package dev.viren.coach.queue;

/**
 * @param key  business key (the goal id); a broker partitions on it, so one goal's messages stay in order
 * @param type the outbox event type, used to route commands to their handler
 */
public record QueueMessage(String topic, String key, String type, String payload, int attempt) {
    public QueueMessage(String topic, String key, String type, String payload) {
        this(topic, key, type, payload, 1);
    }

    public QueueMessage nextAttempt() {
        return new QueueMessage(topic, key, type, payload, attempt + 1);
    }
}
