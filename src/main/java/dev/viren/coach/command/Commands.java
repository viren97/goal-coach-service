package dev.viren.coach.command;

/** Payloads of the command messages on {@code coach.commands}. Small and id-based: handlers re-read state. */
public final class Commands {
    private Commands() {}

    public record GoalCommand(String goalId) {}

    public record TaskCommand(String goalId, String taskId) {}

    public record TopUpCommand(String goalId, String reason) {}
}
