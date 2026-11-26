package dev.viren.coach.queue;

/** Port for async messaging. In-memory by default, Kafka in the docker profile; producers and consumers don't change. */
public interface TaskQueue {
    /** @throws IllegalStateException when the queue cannot accept the message (back-pressure). */
    void publish(QueueMessage message);
}
