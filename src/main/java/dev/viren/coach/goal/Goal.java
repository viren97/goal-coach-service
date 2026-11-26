package dev.viren.coach.goal;

import dev.viren.coach.common.error.CoachException;
import dev.viren.coach.common.error.ErrorCode;
import dev.viren.coach.common.persistence.BaseEntity;
import dev.viren.coach.task.DifficultyState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * "Who I want to become" plus the coaching state for it: the adaptive difficulty, the fatigue counter and
 * the next nudge time. {@code nextNudgeAt} is indexed with status, so the scheduler only reads goals that are due.
 */
@Entity
@Table(name = "goals", indexes = {
        @Index(columnList = "status, next_nudge_at"),
        @Index(columnList = "status, updated_at"),
        @Index(columnList = "user_id")})
public class Goal extends BaseEntity {
    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 300)
    private String identity;

    @Column(length = 2000)
    private String context;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalStatus status = GoalStatus.PLANNING;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(length = 2000)
    private String planSummary;

    @Column(nullable = false)
    private int planAttempts = 1;

    @Column(nullable = false)
    private int currentDifficulty;

    @Column(nullable = false)
    private int doneAtLevel;

    /** Pushed tasks ignored in a row. Drives fatigue backoff; any user action resets it. */
    @Column(nullable = false)
    private int ignoredStreak;

    private Instant nextNudgeAt;

    private Instant lastRecapAt;

    private Instant completedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Goal() {}

    public Goal(String userId, String identity, String context, String idempotencyKey, String requestHash,
                int startDifficulty, Instant now) {
        this.userId = userId;
        this.identity = identity;
        this.context = context;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.currentDifficulty = startDifficulty;
        this.updatedAt = now;
    }

    public void activate(String planSummary, Instant firstNudgeAt, Instant now) {
        requireStatus(GoalStatus.PLANNING);
        this.planSummary = planSummary;
        this.status = GoalStatus.ACTIVE;
        this.nextNudgeAt = firstNudgeAt;
        this.updatedAt = now;
    }

    public void retryPlanning(Instant now) {
        requireStatus(GoalStatus.PLANNING);
        planAttempts++;
        updatedAt = now;
    }

    public void failPlanning(Instant now) {
        requireStatus(GoalStatus.PLANNING);
        status = GoalStatus.PLAN_FAILED;
        nextNudgeAt = null;
        updatedAt = now;
    }

    public void pause(Instant now) {
        requireStatus(GoalStatus.ACTIVE);
        status = GoalStatus.PAUSED;
        nextNudgeAt = null;
        updatedAt = now;
    }

    public void resume(Instant nextNudgeAt, Instant now) {
        requireStatus(GoalStatus.PAUSED);
        status = GoalStatus.ACTIVE;
        ignoredStreak = 0;
        this.nextNudgeAt = nextNudgeAt;
        updatedAt = now;
    }

    public void complete(Instant now) {
        requireStatus(GoalStatus.ACTIVE);
        status = GoalStatus.COMPLETED;
        nextNudgeAt = null;
        completedAt = now;
        updatedAt = now;
    }

    public void scheduleNextNudge(Instant at, Instant now) {
        if (status == GoalStatus.ACTIVE) nextNudgeAt = at;
        updatedAt = now;
    }

    public DifficultyState difficultyState() {
        return new DifficultyState(currentDifficulty, doneAtLevel, ignoredStreak);
    }

    public void applyDifficulty(DifficultyState state, Instant now) {
        this.currentDifficulty = state.difficulty();
        this.doneAtLevel = state.doneAtLevel();
        this.ignoredStreak = state.ignoredStreak();
        this.updatedAt = now;
    }

    public void markRecapRequested(Instant now) {
        lastRecapAt = now;
        updatedAt = now;
    }

    public boolean isActive() { return status == GoalStatus.ACTIVE; }

    private void requireStatus(GoalStatus expected) {
        if (status != expected) {
            throw new CoachException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Goal " + getId() + " is " + status + ", expected " + expected);
        }
    }

    public String getUserId() { return userId; }
    public String getIdentity() { return identity; }
    public String getContext() { return context; }
    public GoalStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getPlanSummary() { return planSummary; }
    public int getPlanAttempts() { return planAttempts; }
    public int getCurrentDifficulty() { return currentDifficulty; }
    public int getDoneAtLevel() { return doneAtLevel; }
    public int getIgnoredStreak() { return ignoredStreak; }
    public Instant getNextNudgeAt() { return nextNudgeAt; }
    public Instant getLastRecapAt() { return lastRecapAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
