package dev.viren.coach.queue;

public final class Topics {
    private Topics() {}

    /** Work to do: generate a plan, top up tasks, send a nudge, write a recap. */
    public static final String COMMANDS = "coach.commands";
    /** Things that happened: task done, milestone completed, recap ready... fanned out to subscribers. */
    public static final String EVENTS = "coach.events";
}
