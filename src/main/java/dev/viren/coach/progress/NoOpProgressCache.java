package dev.viren.coach.progress;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "coach.cache.progress", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpProgressCache implements ProgressCache {
    @Override public Optional<ProgressView> get(String goalId) { return Optional.empty(); }
    @Override public void put(ProgressView view) {}
    @Override public void evict(String goalId) {}
}
