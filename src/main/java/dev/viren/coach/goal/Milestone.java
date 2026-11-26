package dev.viren.coach.goal;

import dev.viren.coach.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** A step on the way to the identity. Exactly one milestone per active goal is ACTIVE; tasks come from it. */
@Entity
@Table(name = "milestones", indexes = @Index(columnList = "goal_id, position"))
public class Milestone extends BaseEntity {
    @Column(nullable = false)
    private String goalId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    /** Tasks to complete before the milestone is reached. */
    @Column(nullable = false)
    private int targetDone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MilestoneStatus status;

    private Instant completedAt;

    protected Milestone() {}

    public Milestone(String goalId, int position, String title, String description, int targetDone, MilestoneStatus status) {
        this.goalId = goalId;
        this.position = position;
        this.title = title;
        this.description = description;
        this.targetDone = targetDone;
        this.status = status;
    }

    public void activate() {
        if (status == MilestoneStatus.LOCKED) status = MilestoneStatus.ACTIVE;
    }

    public void complete(Instant now) {
        if (status == MilestoneStatus.ACTIVE) {
            status = MilestoneStatus.COMPLETED;
            completedAt = now;
        }
    }

    public String getGoalId() { return goalId; }
    public int getPosition() { return position; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getTargetDone() { return targetDone; }
    public MilestoneStatus getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
}
