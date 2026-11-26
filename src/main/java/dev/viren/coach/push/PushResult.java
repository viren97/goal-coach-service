package dev.viren.coach.push;

public enum PushResult {
    DELIVERED,
    /** The token is dead (app uninstalled, token rotated): remove the device. */
    INVALID_TOKEN,
    /** Transient (provider 5xx, 429, network): worth retrying. */
    FAILED
}
