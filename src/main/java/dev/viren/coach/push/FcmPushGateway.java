package dev.viren.coach.push;

import com.google.auth.oauth2.GoogleCredentials;
import dev.viren.coach.config.CoachProperties;
import dev.viren.coach.user.DevicePlatform;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Firebase Cloud Messaging HTTP v1 (covers Android, and iOS through APNs). Authenticates with a service account
 * (FCM_SERVICE_ACCOUNT_JSON, injected from the secret manager). Sends the notification category so the app can
 * render action buttons, and a collapse key so a re-sent nudge replaces rather than duplicates the first one.
 */
@Component
@ConditionalOnProperty(prefix = "coach.push", name = "provider", havingValue = "fcm")
public class FcmPushGateway implements PushGateway {
    private static final Logger log = LoggerFactory.getLogger(FcmPushGateway.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final GoogleCredentials credentials;
    private final RestClient http;
    private final String sendPath;

    public FcmPushGateway(CoachProperties properties, RestClient.Builder builder) throws IOException {
        CoachProperties.Push.Fcm fcm = properties.push().fcm();
        if (fcm.projectId() == null || fcm.projectId().isBlank() || fcm.serviceAccountJson() == null || fcm.serviceAccountJson().isBlank()) {
            throw new IllegalStateException("coach.push.provider=fcm needs FCM_PROJECT_ID and FCM_SERVICE_ACCOUNT_JSON");
        }
        this.credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(fcm.serviceAccountJson().getBytes(StandardCharsets.UTF_8)))
                .createScoped(List.of(SCOPE));
        this.http = builder.baseUrl("https://fcm.googleapis.com").build();
        this.sendPath = "/v1/projects/" + fcm.projectId() + "/messages:send";
    }

    @Override
    public PushResult send(String token, DevicePlatform platform, PushMessage message) {
        try {
            credentials.refreshIfExpired();
            http.post()
                    .uri(sendPath)
                    .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", body(token, message)))
                    .retrieve()
                    .toBodilessEntity();
            return PushResult.DELIVERED;
        } catch (HttpClientErrorException e) {
            String response = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 404 || response.contains("UNREGISTERED")
                    || (e.getStatusCode().value() == 400 && response.contains("registration token"))) {
                return PushResult.INVALID_TOKEN;
            }
            log.warn("FCM rejected push ({}): {}", e.getStatusCode(), response);
            return e.getStatusCode().value() == 429 ? PushResult.FAILED : PushResult.INVALID_TOKEN;
        } catch (RestClientException | IOException e) {
            log.warn("FCM send failed: {}", e.toString());
            return PushResult.FAILED;
        }
    }

    private static Map<String, Object> body(String token, PushMessage m) {
        Map<String, String> data = new LinkedHashMap<>(m.data());
        data.put("category", m.category());
        return Map.of(
                "token", token,
                "notification", Map.of("title", m.title(), "body", m.body()),
                "data", data,
                "android", Map.of("collapse_key", m.collapseKey(),
                        "notification", Map.of("click_action", m.category())),
                "apns", Map.of(
                        "headers", Map.of("apns-collapse-id", m.collapseKey()),
                        "payload", Map.of("aps", Map.of("category", m.category())))
        );
    }
}
