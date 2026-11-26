package dev.viren.coach.support;

import dev.viren.coach.push.PushGateway;
import dev.viren.coach.push.PushMessage;
import dev.viren.coach.push.PushResult;
import dev.viren.coach.user.DevicePlatform;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RecordingPushGateway implements PushGateway {
    public record Sent(String token, DevicePlatform platform, PushMessage message) {}

    private final ConcurrentLinkedQueue<Sent> sent = new ConcurrentLinkedQueue<>();

    @Override
    public PushResult send(String token, DevicePlatform platform, PushMessage message) {
        sent.add(new Sent(token, platform, message));
        return PushResult.DELIVERED;
    }

    public List<PushMessage> forToken(String token) {
        return sent.stream().filter(s -> s.token().equals(token)).map(Sent::message).toList();
    }

    public List<PushMessage> forToken(String token, String category) {
        return forToken(token).stream().filter(m -> m.category().equals(category)).toList();
    }
}
