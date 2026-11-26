package dev.viren.coach.push;

import dev.viren.coach.user.Device;
import dev.viren.coach.user.DeviceRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Sends one message to every device of a user and cleans up dead tokens. */
@Service
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final PushGateway gateway;
    private final DeviceRepository devices;

    public PushService(PushGateway gateway, DeviceRepository devices) {
        this.gateway = gateway;
        this.devices = devices;
    }

    /**
     * @return how many devices received it (0 when the user has no device)
     * @throws PushDeliveryException when every device failed transiently, so the caller's queue retries
     */
    public int sendToUser(String userId, PushMessage message) {
        List<Device> targets = devices.findByUserId(userId);
        int delivered = 0;
        int transientFailures = 0;
        for (Device device : targets) {
            PushResult result = gateway.send(device.getToken(), device.getPlatform(), message);
            switch (result) {
                case DELIVERED -> delivered++;
                case INVALID_TOKEN -> {
                    log.info("Removing dead push token for user {} ({})", userId, device.getPlatform());
                    devices.delete(device);
                }
                case FAILED -> transientFailures++;
            }
        }
        if (delivered == 0 && transientFailures > 0) {
            throw new PushDeliveryException("Push to user " + userId + " failed on all " + transientFailures + " device(s)");
        }
        return delivered;
    }
}
