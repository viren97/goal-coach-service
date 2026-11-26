package dev.viren.coach.push;

import dev.viren.coach.user.DevicePlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local/dev push: writes the notification to the log. */
@Component
@ConditionalOnProperty(prefix = "coach.push", name = "provider", havingValue = "log", matchIfMissing = true)
public class LoggingPushGateway implements PushGateway {
    private static final Logger log = LoggerFactory.getLogger(LoggingPushGateway.class);

    @Override
    public PushResult send(String token, DevicePlatform platform, PushMessage message) {
        log.info("[push {} {}] {} - {} {}", platform, abbreviate(token), message.title(), message.body(), message.data());
        return PushResult.DELIVERED;
    }

    private static String abbreviate(String token) {
        return token.length() <= 8 ? token : token.substring(0, 8) + "…";
    }
}
