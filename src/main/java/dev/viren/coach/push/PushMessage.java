package dev.viren.coach.push;

import java.util.Map;

/**
 * @param category    notification category: the app maps it to action buttons
 *                    (COACH_TASK: Done / Snooze / Swap; COACH_CHECKIN: Easier / Keep / Pause; COACH_INFO: none)
 * @param collapseKey same key = the device shows one notification, so an at-least-once re-send doesn't duplicate it
 * @param data        deep link and ids the app needs to act without opening a screen
 */
public record PushMessage(String title, String body, String category, String collapseKey, Map<String, String> data) {
    public static final String CATEGORY_TASK = "COACH_TASK";
    public static final String CATEGORY_CHECK_IN = "COACH_CHECKIN";
    public static final String CATEGORY_INFO = "COACH_INFO";
}
