package dev.viren.coach.push;

import dev.viren.coach.user.DevicePlatform;

/** Port to the push provider. Adapters: {@link LoggingPushGateway} (default) and {@link FcmPushGateway}. */
public interface PushGateway {
    PushResult send(String token, DevicePlatform platform, PushMessage message);
}
