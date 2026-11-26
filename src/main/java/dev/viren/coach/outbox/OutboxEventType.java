package dev.viren.coach.outbox;

import dev.viren.coach.queue.Topics;

public enum OutboxEventType {
    GENERATE_PLAN(Topics.COMMANDS),
    TOP_UP_TASKS(Topics.COMMANDS),
    SEND_NUDGE(Topics.COMMANDS),
    GENERATE_RECAP(Topics.COMMANDS),
    COACH_EVENT(Topics.EVENTS);

    private final String topic;

    OutboxEventType(String topic) { this.topic = topic; }

    public String topic() { return topic; }
}
