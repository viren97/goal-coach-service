package dev.viren.coach.progress;

import dev.viren.coach.event.CoachEvent;
import dev.viren.coach.event.EventSubscriber;
import org.springframework.stereotype.Component;

/** Background changes (expiry, recaps, plan ready) evict through events; user actions evict synchronously. */
@Component
public class ProgressCacheEvictor implements EventSubscriber {
    private final ProgressCache cache;

    public ProgressCacheEvictor(ProgressCache cache) {
        this.cache = cache;
    }

    @Override
    public String name() {
        return "progress-cache-evictor";
    }

    @Override
    public void onEvent(CoachEvent event) {
        cache.evict(event.goalId());
    }
}
