package dev.viren.coach.command;

import dev.viren.coach.outbox.OutboxEventType;

/**
 * One handler per command type (strategy + registry). Handlers must be idempotent: a command can be delivered
 * more than once (outbox re-send, broker redelivery, sweeper retry). Throwing = transient failure, retried by the queue.
 */
public interface CommandHandler {
    OutboxEventType type();

    void handle(String payload) throws Exception;
}
