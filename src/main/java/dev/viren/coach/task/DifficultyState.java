package dev.viren.coach.task;

/**
 * @param difficulty   current level, 1 (tiny) .. 5 (stretch)
 * @param doneAtLevel  tasks done in a row at this level
 * @param ignoredStreak pushed tasks ignored in a row
 */
public record DifficultyState(int difficulty, int doneAtLevel, int ignoredStreak) {}
