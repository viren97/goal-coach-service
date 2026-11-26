package dev.viren.coach.task;

import dev.viren.coach.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** One small action (5-30 min). Lifecycle rules live in {@link TaskState}; this class only holds the data. */
@Entity
@Table(name = "tasks", indexes = {
        @Index(columnList = "milestone_id, status"),
        @Index(columnList = "goal_id, status"),
        @Index(columnList = "status, updated_at"),
        @Index(columnList = "status, sent_at"),
        @Index(columnList = "user_id, status")})
public class CoachTask extends BaseEntity {
    @Column(nullable = false)
    private String goalId;

    @Column(nullable = false)
    private String milestoneId;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskKind kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String body;

    @Column(length = 1000)
    private String resourceUrl;

    @Column(nullable = false)
    private int difficulty;

    @Column(nullable = false)
    private int estMinutes;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.QUEUED;

    /** Snoozed tasks are not picked before this instant. */
    private Instant notBefore;

    private Instant sentAt;

    /** True when a push actually reached a device; only those count as "ignored" when they expire. */
    @Column(nullable = false)
    private boolean pushed;

    private Instant actedAt;

    @Column(nullable = false)
    private int snoozeCount;

    @Column(length = 1000)
    private String note;

    @Column(length = 1000)
    private String proofUrl;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CoachTask() {}

    public CoachTask(String goalId, String milestoneId, String userId, TaskKind kind, String title, String body,
                     String resourceUrl, int difficulty, int estMinutes, int position, TaskOrigin origin, Instant now) {
        this.goalId = goalId;
        this.milestoneId = milestoneId;
        this.userId = userId;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.resourceUrl = resourceUrl;
        this.difficulty = difficulty;
        this.estMinutes = estMinutes;
        this.position = position;
        this.origin = origin;
        this.updatedAt = now;
    }

    // ---- lifecycle (delegates to the state object) ----
    public void schedule(Instant now) { state().schedule(this, now); }
    public void markSent(boolean pushed, Instant now) { state().markSent(this, pushed, now); }
    public void complete(String note, String proofUrl, Instant now) {
        state().complete(this, now);
        this.note = note;
        this.proofUrl = proofUrl;
    }
    public void snooze(Instant until, Instant now) { state().snooze(this, until, now); }
    public void swap(Instant now) { state().swap(this, now); }
    public void expire(Instant now) { state().expire(this, now); }
    public void requeue(Instant now) { state().requeue(this, now); }

    public TaskState state() { return TaskState.of(status); }

    public boolean isAvailableAt(Instant now) {
        return status == TaskStatus.QUEUED && (notBefore == null || !notBefore.isAfter(now));
    }

    // ---- used only by TaskState ----
    void moveTo(TaskStatus target, Instant now) {
        this.status = target;
        this.updatedAt = now;
    }

    void recordSent(boolean pushed, Instant now) {
        this.sentAt = now;
        this.pushed = pushed;
    }

    void setActedAt(Instant now) { this.actedAt = now; }

    void snoozeUntil(Instant until) {
        this.notBefore = until;
        this.snoozeCount++;
    }

    public String getGoalId() { return goalId; }
    public String getMilestoneId() { return milestoneId; }
    public String getUserId() { return userId; }
    public TaskKind getKind() { return kind; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getResourceUrl() { return resourceUrl; }
    public int getDifficulty() { return difficulty; }
    public int getEstMinutes() { return estMinutes; }
    public int getPosition() { return position; }
    public TaskOrigin getOrigin() { return origin; }
    public TaskStatus getStatus() { return status; }
    public Instant getNotBefore() { return notBefore; }
    public Instant getSentAt() { return sentAt; }
    public boolean isPushed() { return pushed; }
    public Instant getActedAt() { return actedAt; }
    public int getSnoozeCount() { return snoozeCount; }
    public String getNote() { return note; }
    public String getProofUrl() { return proofUrl; }
    public Instant getUpdatedAt() { return updatedAt; }
}
