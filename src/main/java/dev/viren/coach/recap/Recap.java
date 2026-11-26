package dev.viren.coach.recap;

import dev.viren.coach.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/** One weekly recap per goal per week (unique), so a re-run of the recap command cannot create a second one. */
@Entity
@Table(name = "recaps", uniqueConstraints = @UniqueConstraint(columnNames = {"goal_id", "week_start"}))
public class Recap extends BaseEntity {
    @Column(nullable = false)
    private String goalId;

    @Column(nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(length = 1000)
    private String highlights;

    @Column(nullable = false)
    private int tasksDone;

    @Column(nullable = false)
    private int difficultyChange;

    protected Recap() {}

    public Recap(String goalId, LocalDate weekStart, String message, String highlights, int tasksDone, int difficultyChange) {
        this.goalId = goalId;
        this.weekStart = weekStart;
        this.message = message;
        this.highlights = highlights;
        this.tasksDone = tasksDone;
        this.difficultyChange = difficultyChange;
    }

    public String getGoalId() { return goalId; }
    public LocalDate getWeekStart() { return weekStart; }
    public String getMessage() { return message; }
    public String getHighlights() { return highlights; }
    public int getTasksDone() { return tasksDone; }
    public int getDifficultyChange() { return difficultyChange; }
}
