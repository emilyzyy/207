package closeai.adapters.viewmodels;

import java.time.LocalTime;
import java.util.Locale;

/**
 * Turns a {@link LocalTime} into the clock people actually read, and back again.
 *
 * <p>Presentation only. Scheduling, validation, persistence and every API still work in
 * {@code LocalTime}; this is the last step before a string reaches a label, and the first
 * step after one leaves a text field. Nothing here changes what a time <em>is</em>.</p>
 *
 * <p>Parsing is deliberately generous. Someone editing "9:00 AM" may type {@code 9},
 * {@code 9am}, {@code 9:30 pm} or paste back the {@code 09:00} the field used to hold, and
 * all of those mean something unambiguous. Being strict here would only invent errors the
 * traveller has to decode, and the use case validates the result again regardless.</p>
 */
public final class TimeDisplay {

    private TimeDisplay() {
    }

    /** {@code 09:00 -> "9:00 AM"}, {@code 13:15 -> "1:15 PM"}. Empty for null. */
    public static String format(LocalTime time) {
        if (time == null) {
            return "";
        }
        int hour = time.getHour() % 12;
        if (hour == 0) {
            hour = 12;
        }
        String meridiem = time.getHour() < 12 ? "AM" : "PM";
        return String.format(Locale.ROOT, "%d:%02d %s", hour, time.getMinute(), meridiem);
    }

    /** A start-to-end range, as {@code "9:00 AM – 10:00 AM"}. */
    public static String range(LocalTime start, LocalTime end) {
        return format(start) + " – " + format(end);
    }

    /**
     * Reads a time the traveller typed, or null when it cannot be understood.
     *
     * <p>Accepts {@code 9}, {@code 9am}, {@code 9 AM}, {@code 9:30pm}, {@code 12:05 AM} and
     * the 24-hour {@code 09:00} / {@code 13:15} the fields used to contain, so an older
     * habit still works.</p>
     */
    public static LocalTime parse(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.trim().toUpperCase(Locale.ROOT).replace(".", "");
        if (cleaned.isEmpty()) {
            return null;
        }

        boolean pm = cleaned.endsWith("PM");
        boolean am = cleaned.endsWith("AM");
        if (am || pm) {
            cleaned = cleaned.substring(0, cleaned.length() - 2).trim();
        }

        String[] parts = cleaned.split(":");
        if (parts.length > 2) {
            return null;
        }
        int hour;
        int minute = 0;
        try {
            hour = Integer.parseInt(parts[0].trim());
            if (parts.length == 2) {
                String minutes = parts[1].trim();
                // "9:5" is a typo, not five minutes past; two digits are required.
                if (minutes.length() != 2) {
                    return null;
                }
                minute = Integer.parseInt(minutes);
            }
        } catch (NumberFormatException notANumber) {
            return null;
        }
        if (minute < 0 || minute > 59) {
            return null;
        }

        if (am || pm) {
            if (hour < 1 || hour > 12) {
                return null;
            }
            if (hour == 12) {
                hour = 0;
            }
            if (pm) {
                hour += 12;
            }
        } else if (hour < 0 || hour > 23) {
            return null;
        }

        try {
            return LocalTime.of(hour, minute);
        } catch (java.time.DateTimeException outOfRange) {
            return null;
        }
    }
}
