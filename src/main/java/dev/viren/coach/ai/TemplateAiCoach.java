package dev.viren.coach.ai;

import dev.viren.coach.ai.AiModels.GeneratedMilestone;
import dev.viren.coach.ai.AiModels.GeneratedPlan;
import dev.viren.coach.ai.AiModels.GeneratedRecap;
import dev.viren.coach.ai.AiModels.GeneratedTask;
import dev.viren.coach.ai.AiModels.GeneratedTaskBatch;
import dev.viren.coach.ai.AiModels.PlanRequest;
import dev.viren.coach.ai.AiModels.RecapRequest;
import dev.viren.coach.ai.AiModels.TaskBatchRequest;
import dev.viren.coach.task.TaskKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic coach with no external calls. Default for local runs and tests, and the fallback whenever the
 * real model fails, refuses or returns something invalid, so a user always gets a plan.
 */
@Component
public class TemplateAiCoach implements AiCoach {

    private static final String[][] MILESTONES = {
            {"Foundations", "Understand what %s really involves and take the first small steps."},
            {"Building the habit", "Practise regularly until acting like %s feels normal."},
            {"Owning it", "Do the things %s does, in public, without being asked."},
    };

    private static final String[][] LEARN = {
            {"Read one short article about how people become %s", "Pick one article (under 5 minutes) and note one idea you can try today."},
            {"Watch a 5-minute talk related to being %s", "Write down the single most useful tip from it."},
            {"Study one example of someone who is %s", "Note one thing they do daily that you don't yet."},
    };

    private static final String[][] DO = {
            {"Spend 10 focused minutes practising as %s", "Set a timer, do the smallest real version of the skill, then stop."},
            {"Tell one person you're working on becoming %s", "Saying it out loud makes it real. Note how it felt."},
            {"Plan tomorrow's 15 minutes of practice as %s", "Write when, where and what you will do."},
            {"Do one thing today that %s would do", "Keep it small enough to finish in 15 minutes."},
    };

    @Override
    public GeneratedPlan generatePlan(PlanRequest request) {
        String who = request.identity();
        List<GeneratedMilestone> milestones = new ArrayList<>();
        for (int m = 0; m < MILESTONES.length; m++) {
            List<GeneratedTask> tasks = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                int difficulty = clamp(request.startDifficulty() + m + (i >= 3 ? 1 : 0));
                tasks.add(task(i, m * 5 + i, who, difficulty, ""));
            }
            milestones.add(new GeneratedMilestone(MILESTONES[m][0], MILESTONES[m][1].formatted(who), tasks));
        }
        return new GeneratedPlan(
                "Three milestones that move you from learning about being " + who + " to living it, one small task at a time.",
                milestones);
    }

    @Override
    public GeneratedTaskBatch generateTasks(TaskBatchRequest request) {
        Set<String> recent = new HashSet<>(request.recentTitles());
        List<GeneratedTask> tasks = new ArrayList<>();
        int seed = request.recentTitles().size();
        while (tasks.size() < request.count()) {
            String suffix = " (round " + (seed / (LEARN.length + DO.length) + 2) + ")";
            GeneratedTask t = task(seed, seed, request.identity(), clamp(request.difficulty()), suffix);
            if (!recent.contains(t.title())) tasks.add(t);
            seed++;
        }
        return new GeneratedTaskBatch(tasks);
    }

    @Override
    public GeneratedRecap weeklyRecap(RecapRequest r) {
        int change = (r.done() >= 5 && r.ignored() == 0) ? 1 : (r.ignored() > r.done() ? -1 : 0);
        String message = r.done() == 0
                ? "A quiet week. One 5-minute task tomorrow is all it takes to restart: you're still on your way to being " + r.identity() + "."
                : "You finished " + r.done() + " task" + (r.done() == 1 ? "" : "s") + " this week as " + r.identity()
                  + (r.streakDays() > 1 ? ", with a " + r.streakDays() + "-day streak" : "") + ". Keep going.";
        List<String> highlights = r.doneTitles().stream().limit(3).toList();
        return new GeneratedRecap(message, highlights, change);
    }

    private static GeneratedTask task(int i, int salt, String who, int difficulty, String suffix) {
        boolean learn = i % 2 == 0;
        String[][] pool = learn ? LEARN : DO;
        String[] t = pool[Math.floorMod(salt, pool.length)];
        return new GeneratedTask(learn ? TaskKind.LEARN : TaskKind.DO,
                t[0].formatted(who) + suffix, t[1], "", difficulty, learn ? 5 + difficulty * 2 : 10 + difficulty * 3);
    }

    private static int clamp(int difficulty) {
        return Math.max(1, Math.min(5, difficulty));
    }
}
