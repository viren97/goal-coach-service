package dev.viren.coach.progress;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Progress is read on every app open but changes only on actions. Entries are evicted after every change
 * (synchronously after an action commits, via events for background changes) and expire after a TTL as a backstop.
 * Redis down = cache miss, never an error.
 */
@Component
@ConditionalOnProperty(prefix = "coach.cache.progress", name = "enabled", havingValue = "true")
public class RedisProgressCache implements ProgressCache {
    private static final Logger log = LoggerFactory.getLogger(RedisProgressCache.class);
    private static final String PREFIX = "coach:progress:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisProgressCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                              @Value("${coach.cache.progress.ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Optional<ProgressView> get(String goalId) {
        try {
            String json = redis.opsForValue().get(PREFIX + goalId);
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, ProgressView.class));
        } catch (Exception e) {
            log.warn("Progress cache read failed for {}: {}", goalId, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(ProgressView view) {
        try {
            redis.opsForValue().set(PREFIX + view.goalId(), objectMapper.writeValueAsString(view), ttl);
        } catch (Exception e) {
            log.warn("Progress cache write failed for {}: {}", view.goalId(), e.toString());
        }
    }

    @Override
    public void evict(String goalId) {
        try {
            redis.delete(PREFIX + goalId);
        } catch (Exception e) {
            log.warn("Progress cache evict failed for {}: {}", goalId, e.toString());
        }
    }
}
