package dev.viren.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.viren.coach.command.Commands.TaskCommand;
import dev.viren.coach.common.error.CoachException;
import dev.viren.coach.common.error.ErrorCode;
import dev.viren.coach.goal.Goal;
import dev.viren.coach.goal.GoalRepository;
import dev.viren.coach.goal.GoalService;
import dev.viren.coach.goal.GoalStatus;
import dev.viren.coach.goal.Milestone;
import dev.viren.coach.goal.MilestoneStatus;
import dev.viren.coach.nudge.NudgeScheduler;
import dev.viren.coach.nudge.SendNudgeHandler;
import dev.viren.coach.progress.ProgressService;
import dev.viren.coach.progress.ProgressView;
import dev.viren.coach.push.PushMessage;
import dev.viren.coach.recap.RecapRepository;
import dev.viren.coach.recap.RecapScheduler;
import dev.viren.coach.recovery.StuckWorkSweeper;
import dev.viren.coach.support.MutableClock;
import dev.viren.coach.support.RecordingPushGateway;
import dev.viren.coach.task.CoachTask;
import dev.viren.coach.task.TaskAction;
import dev.viren.coach.task.TaskExpiryJob;
import dev.viren.coach.task.TaskRepository;
import dev.viren.coach.task.TaskService;
import dev.viren.coach.task.TaskStatus;
import dev.viren.coach.user.DevicePlatform;
import dev.viren.coach.user.Intensity;
import dev.viren.coach.user.User;
import dev.viren.coach.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * End to end on a real SQLite DB through outbox -> queue -> handlers -> push -> actions -> progress,
 * with the template AI coach, a recording push gateway and a clock the test moves.
 */
@SpringBootTest
@ActiveProfiles("test")
class CoachFlowIntegrationTest {

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-11-23T03:30:00Z")); // Monday 09:00 IST
        }

        @Bean
        @Primary
        RecordingPushGateway recordingPushGateway() {
            return new RecordingPushGateway();
        }
    }

    @Autowired MutableClock clock;
    @Autowired RecordingPushGateway pushes;
    @Autowired UserService users;
    @Autowired GoalService goals;
    @Autowired GoalRepository goalRepository;
    @Autowired TaskService taskService;
    @Autowired TaskRepository taskRepository;
    @Autowired ProgressService progress;
    @Autowired NudgeScheduler scheduler;
    @Autowired SendNudgeHandler sendNudgeHandler;
    @Autowired TaskExpiryJob expiryJob;
    @Autowired StuckWorkSweeper sweeper;
    @Autowired RecapScheduler.Claimer recapClaimer;
    @Autowired RecapRepository recaps;
    @Autowired ObjectMapper objectMapper;

    record Fixture(User user, String token, Goal goal) {}

    @Test
    void onboardingPlanNudgeDoneAndProgress() {
        Fixture f = activeGoal("a confident public speaker");

        List<Milestone> milestones = goals.milestones(f.goal().getId());
        assertThat(milestones).hasSize(3);
        assertThat(milestones.get(0).getStatus()).isEqualTo(MilestoneStatus.ACTIVE);
        assertThat(milestones.subList(1, 3)).allMatch(m -> m.getStatus() == MilestoneStatus.LOCKED);
        assertThat(taskRepository.findByGoalIdOrderByPositionAsc(f.goal().getId())).hasSize(15)
                .allMatch(t -> t.getStatus() == TaskStatus.QUEUED);

        CoachTask sent = nudge(f);
        PushMessage push = pushes.forToken(f.token(), PushMessage.CATEGORY_TASK).get(0);
        assertThat(push.data()).containsEntry("taskId", sent.getId()).containsKey("deepLink");
        assertThat(push.collapseKey()).isEqualTo("task-" + sent.getId());
        assertThat(goals.get(f.goal().getId()).getNextNudgeAt()).isAfter(clock.instant());

        var result = taskService.act(sent.getId(), TaskAction.DONE, "Did it in the mirror", null, null);
        assertThat(result.task().getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(taskService.act(sent.getId(), TaskAction.DONE, null, null, null).replayed()).isTrue();

        ProgressView view = progress.get(f.goal().getId());
        assertThat(view.tasksDone()).isEqualTo(1);
        assertThat(view.identityScore()).isEqualTo(sent.getDifficulty());
        assertThat(view.currentStreakDays()).isEqualTo(1);
        assertThat(view.pushActionRate()).isEqualTo(1.0);
        assertThat(view.milestones().get(0).done()).isEqualTo(1);
    }

    @Test
    void goalCreationIsIdempotent() {
        User user = newUser();
        String key = UUID.randomUUID().toString();
        GoalService.Submission first = goals.create(user.getId(), key, "a runner", "I can jog 2km");
        GoalService.Submission replay = goals.create(user.getId(), key, "a runner", "I can jog 2km");
        assertThat(first.created()).isTrue();
        assertThat(replay.created()).isFalse();
        assertThat(replay.goal().getId()).isEqualTo(first.goal().getId());

        assertThatThrownBy(() -> goals.create(user.getId(), key, "a swimmer", null))
                .isInstanceOf(CoachException.class)
                .satisfies(e -> assertThat(((CoachException) e).code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThatThrownBy(() -> goals.create(user.getId(), UUID.randomUUID().toString(), "a swimmer", null))
                .isInstanceOf(CoachException.class)
                .satisfies(e -> assertThat(((CoachException) e).code()).isEqualTo(ErrorCode.GOAL_LIMIT_REACHED));
    }

    @Test
    void duplicateSendNudgeDeliversOnce() throws Exception {
        Fixture f = activeGoal("a writer");
        CoachTask sent = nudge(f);
        sendNudgeHandler.handle(objectMapper.writeValueAsString(new TaskCommand(f.goal().getId(), sent.getId())));
        assertThat(pushes.forToken(f.token(), PushMessage.CATEGORY_TASK)).hasSize(1);
    }

    @Test
    void swapSendsAReplacementRightAway() {
        Fixture f = activeGoal("a calm parent");
        CoachTask first = nudge(f);

        taskService.act(first.getId(), TaskAction.SWAP, null, null, null);
        assertThat(goals.get(f.goal().getId()).getNextNudgeAt()).isBeforeOrEqualTo(clock.instant());

        scheduler.tick();
        await().until(() -> pushes.forToken(f.token(), PushMessage.CATEGORY_TASK).size() == 2);
        String secondId = pushes.forToken(f.token(), PushMessage.CATEGORY_TASK).get(1).data().get("taskId");
        assertThat(secondId).isNotEqualTo(first.getId());
    }

    @Test
    void ignoredPushesBackOffEaseUpAndAskToCheckIn() {
        Fixture f = activeGoal("a morning person");
        for (int i = 0; i < 3; i++) {
            Instant due = goals.get(f.goal().getId()).getNextNudgeAt();
            clock.setIfLater(due);
            nudge(f);
            clock.advance(Duration.ofHours(7)); // past ignore-after (6h)
            expiryJob.run();
        }
        Goal goal = goals.get(f.goal().getId());
        assertThat(goal.getIgnoredStreak()).isEqualTo(3);
        assertThat(goal.getCurrentDifficulty()).isEqualTo(1); // 2 -> 1 after two ignored
        await().until(() -> !pushes.forToken(f.token(), PushMessage.CATEGORY_CHECK_IN).isEmpty());

        Goal afterCheckIn = goals.checkIn(goal.getId(), GoalService.CheckInAnswer.KEEP);
        assertThat(afterCheckIn.getIgnoredStreak()).isZero();
    }

    @Test
    void finishingAMilestoneUnlocksTheNextAndCelebrates() {
        Fixture f = activeGoal("a guitarist");
        Milestone first = goals.milestones(f.goal().getId()).get(0);
        taskRepository.findByMilestoneIdAndStatus(first.getId(), TaskStatus.QUEUED)
                .forEach(t -> taskService.act(t.getId(), TaskAction.DONE, null, null, null));

        List<Milestone> after = goals.milestones(f.goal().getId());
        assertThat(after.get(0).getStatus()).isEqualTo(MilestoneStatus.COMPLETED);
        assertThat(after.get(1).getStatus()).isEqualTo(MilestoneStatus.ACTIVE);
        await().until(() -> pushes.forToken(f.token(), PushMessage.CATEGORY_INFO).stream()
                .anyMatch(m -> m.title().startsWith("Milestone reached")));
        assertThat(goals.get(f.goal().getId()).getCurrentDifficulty()).isGreaterThan(2); // 5 done in a row
    }

    @Test
    void sweeperRecoversAPlanWhoseMessageWasLost() {
        User user = newUser();
        // Goal row exists but its GENERATE_PLAN message never made it (e.g. crash, in-memory queue lost on restart).
        Goal stranded = goalRepository.save(new Goal(user.getId(), "a chess player", null, UUID.randomUUID().toString(),
                "hash", 2, clock.instant().minus(Duration.ofMinutes(10))));
        assertThat(sweeper.sweep()).isGreaterThanOrEqualTo(1);
        await().until(() -> goals.get(stranded.getId()).getStatus() == GoalStatus.ACTIVE);
        assertThat(goals.milestones(stranded.getId())).isNotEmpty();
    }

    @Test
    void weeklyRecapIsWrittenOnceAndPushed() {
        Fixture f = activeGoal("a reader");
        CoachTask sent = nudge(f);
        taskService.act(sent.getId(), TaskAction.DONE, null, null, null);

        assertThat(recapClaimer.claim(f.goal().getId())).isTrue();
        assertThat(recapClaimer.claim(f.goal().getId())).isFalse(); // already requested this week
        await().until(() -> !recaps.findByGoalIdOrderByWeekStartDesc(f.goal().getId()).isEmpty());
        assertThat(recaps.findByGoalIdOrderByWeekStartDesc(f.goal().getId())).hasSize(1);
        await().until(() -> pushes.forToken(f.token(), PushMessage.CATEGORY_INFO).stream()
                .anyMatch(m -> m.title().startsWith("Your week as")));
    }

    // ---------- helpers ----------

    private User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.create("User " + id, id + "@example.com", "Asia/Kolkata", Intensity.STEADY, 0, 24);
    }

    private Fixture activeGoal(String identity) {
        User user = newUser();
        String token = "token-" + UUID.randomUUID();
        users.registerDevice(user.getId(), token, DevicePlatform.ANDROID);
        GoalService.Submission s = goals.create(user.getId(), UUID.randomUUID().toString(), identity, null);
        assertThat(s.created()).isTrue();
        assertThat(s.goal().getStatus()).isEqualTo(GoalStatus.PLANNING);
        await().until(() -> goals.get(s.goal().getId()).getStatus() == GoalStatus.ACTIVE);
        return new Fixture(user, token, goals.get(s.goal().getId()));
    }

    /** Runs the scheduler and waits until a new task push for this fixture is delivered and marked SENT. */
    private CoachTask nudge(Fixture f) {
        int before = pushes.forToken(f.token(), PushMessage.CATEGORY_TASK).size();
        scheduler.tick();
        await().until(() -> pushes.forToken(f.token(), PushMessage.CATEGORY_TASK).size() == before + 1);
        String taskId = pushes.forToken(f.token(), PushMessage.CATEGORY_TASK).get(before).data().get("taskId");
        await().until(() -> taskRepository.findById(taskId).orElseThrow().getStatus() == TaskStatus.SENT);
        return taskRepository.findById(taskId).orElseThrow();
    }
}
