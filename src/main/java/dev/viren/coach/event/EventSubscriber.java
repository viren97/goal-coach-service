package dev.viren.coach.event;

/** Anything that reacts to coach events (notifications, cache eviction, analytics). Register a bean to subscribe. */
public interface EventSubscriber {
    String name();

    void onEvent(CoachEvent event);
}
