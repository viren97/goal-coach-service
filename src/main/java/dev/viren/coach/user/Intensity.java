package dev.viren.coach.user;

/** How hard the coach pushes: nudges per day. Users pick it; fatigue can temporarily lower it to 1. */
public enum Intensity {
    CHILL(1), STEADY(2), INTENSE(3);

    private final int pushesPerDay;

    Intensity(int pushesPerDay) { this.pushesPerDay = pushesPerDay; }

    public int pushesPerDay() { return pushesPerDay; }
}
