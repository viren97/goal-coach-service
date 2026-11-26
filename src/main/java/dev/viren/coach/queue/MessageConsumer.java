package dev.viren.coach.queue;

/** Exactly one consumer per topic; fan-out to many listeners is done by the consumer itself. */
public interface MessageConsumer {
    String topic();

    void consume(QueueMessage message);
}
