package dev.viren.coach.progress;

import java.util.Optional;

/** Port for caching progress views. No-op by default, Redis in the docker profile. Must fail open. */
public interface ProgressCache {
    Optional<ProgressView> get(String goalId);

    void put(ProgressView view);

    void evict(String goalId);
}
