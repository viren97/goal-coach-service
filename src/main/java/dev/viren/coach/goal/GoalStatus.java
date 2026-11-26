package dev.viren.coach.goal;

public enum GoalStatus {
    /** Accepted; the AI plan is being generated in the background. */
    PLANNING,
    ACTIVE,
    PAUSED,
    COMPLETED,
    /** Plan generation kept failing past coach.sweeper.max-plan-attempts. */
    PLAN_FAILED,
    ARCHIVED;

    public boolean isOpen() {
        return this == PLANNING || this == ACTIVE || this == PAUSED;
    }
}
