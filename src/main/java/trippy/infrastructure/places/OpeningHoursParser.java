package trippy.infrastructure.places;

import trippy.domain.valueobjects.OpeningHours;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns an OpenStreetMap {@code opening_hours} tag into normalised weekday intervals.
 *
 * <p>This lives in the infrastructure layer on purpose: the syntax is a quirk of one
 * provider, and nothing above this package should ever have to know that {@code "Mo-Fr
 * 09:00-17:00; Sa 10:00-14:00; Su off"} is a thing. The use-case layer receives
 * {@link OpeningHours} and nothing else.</p>
 *
 *
 * <p>The full <a href="https://wiki.openstreetmap.org/wiki/Key:opening_hours">specification</a>
 * is very large — month ranges, week numbers, "second Monday", sunset-relative times, holiday
 * selectors. Implementing all of it would be a project of its own, so this handles the
 * common shape and is deliberately <em>strict</em> about the rest: anything it does not fully
 * understand yields {@link OpeningHours#unknown()} rather than a guess. A wrong guess would
 * silently make a venue unschedulable or schedule a visit to a shut door; unknown at least
 * tells the truth and keeps the current permissive behaviour.</p>
 *
 * <h2>Understood</h2>
 * <ul>
 *   <li>{@code 24/7}</li>
 *   <li>Weekday selectors: {@code Mo}, {@code Mo-Fr}, {@code Mo,We,Fr}, {@code Mo-We,Sa}</li>
 *   <li>Several intervals in a day: {@code Mo 09:00-12:00,13:00-17:00}</li>
 *   <li>Overnight spans: {@code Fr 20:00-02:00}, split at midnight onto both days</li>
 *   <li>{@code off} and {@code closed}, which make those days closed</li>
 *   <li>Rules with no weekday selector, which apply to the whole week</li>
 *   <li>Later rules overriding earlier ones for the days they name, as the {@code ;}
 *       separator means in the specification</li>
 * </ul>
 *
 * <h2>Deliberately not understood</h2>
 *
 * <p>Month and date ranges, week selectors, nth-weekday selectors, {@code sunrise}/
 * {@code sunset}, and comment strings all yield unknown. Public- and school-holiday rules
 * ({@code PH}, {@code SH}) are the one exception: they are <em>skipped</em> rather than
 * rejected, so that a perfectly good {@code "Mo-Fr 09:00-17:00; PH off"} still yields
 * weekday hours. The cost is that this parser does not know when a public holiday is and
 * will treat one as an ordinary weekday.</p>
 */
public final class OpeningHoursParser {

    /**
     * Midnight at the end of a day, as the last minute of it.
     *
     * <p>{@code 24:00} and a span running to midnight cannot be written as a {@code LocalTime}
     * on the same day. The scheduler works in whole minutes inside a single day, so the last
     * minute is the faithful representation and costs at most one minute of a closing time.</p>
     */
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59);

    private static final Map<String, DayOfWeek> DAY_NAMES = new LinkedHashMap<>();

    static {
        DAY_NAMES.put("mo", DayOfWeek.MONDAY);
        DAY_NAMES.put("tu", DayOfWeek.TUESDAY);
        DAY_NAMES.put("we", DayOfWeek.WEDNESDAY);
        DAY_NAMES.put("th", DayOfWeek.THURSDAY);
        DAY_NAMES.put("fr", DayOfWeek.FRIDAY);
        DAY_NAMES.put("sa", DayOfWeek.SATURDAY);
        DAY_NAMES.put("su", DayOfWeek.SUNDAY);
    }

    private OpeningHoursParser() {
    }

    /**
     * Parses a raw tag value.
     *
     * @param raw the {@code opening_hours} tag, which may be null, blank or nonsense
     * @return normalised hours, or {@link OpeningHours#unknown()} when the value is absent
     *         or not fully understood — never an exception
     */
    public static OpeningHours parse(String raw) {
        if (raw == null) {
            return OpeningHours.unknown();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return OpeningHours.unknown();
        }
        if ("24/7".equals(value) || "24 hours".equals(value) || "open".equals(value)) {
            return OpeningHours.alwaysOpen();
        }
        // A comment in quotes could say anything at all, including "by appointment".
        if (value.indexOf('"') >= 0) {
            return OpeningHours.unknown();
        }

        Map<DayOfWeek, List<OpeningHours.TimeInterval>> week = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            week.put(day, new ArrayList<OpeningHours.TimeInterval>());
        }
        boolean anyRuleApplied = false;

        for (String rule : value.split(";")) {
            String trimmed = rule.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("ph") || trimmed.startsWith("sh")) {
                // Holidays are real but we do not have a calendar of them. Skipping the rule
                // keeps the ordinary week usable; the limitation is documented above.
                continue;
            }
            if (!applyRule(trimmed, week)) {
                return OpeningHours.unknown();
            }
            anyRuleApplied = true;
        }
        if (!anyRuleApplied) {
            return OpeningHours.unknown();
        }
        return OpeningHours.of(week);
    }

    /**
     * Applies one {@code ;}-separated rule, replacing whatever the days it names had before.
     *
     * @return false if the rule was not understood, in which case the caller gives up entirely
     */
    private static boolean applyRule(String rule,
                                     Map<DayOfWeek, List<OpeningHours.TimeInterval>> week) {
        String selector;
        String times;
        int split = indexOfTimesPart(rule);
        if (split < 0) {
            selector = "";
            times = rule;
        } else {
            selector = rule.substring(0, split).trim();
            times = rule.substring(split).trim();
        }

        List<DayOfWeek> days = selector.isEmpty()
                ? new ArrayList<>(java.util.Arrays.asList(DayOfWeek.values()))
                : parseDays(selector);
        if (days == null) {
            return false;
        }

        if ("off".equals(times) || "closed".equals(times)) {
            for (DayOfWeek day : days) {
                week.get(day).clear();
            }
            return true;
        }

        List<OpeningHours.TimeInterval> sameDay = new ArrayList<>();
        List<OpeningHours.TimeInterval> nextDay = new ArrayList<>();
        for (String span : times.split(",")) {
            if (!parseSpan(span.trim(), sameDay, nextDay)) {
                return false;
            }
        }

        for (DayOfWeek day : days) {
            week.get(day).clear();
            week.get(day).addAll(sameDay);
        }
        // The tail of an overnight span belongs to the following morning, and is added to
        // whatever that morning already has rather than replacing it.
        for (DayOfWeek day : days) {
            week.get(day.plus(1)).addAll(nextDay);
        }
        return true;
    }

    /** Where the weekday selector stops and the times begin: the first digit or "off". */
    private static int indexOfTimesPart(String rule) {
        for (int i = 0; i < rule.length(); i++) {
            if (Character.isDigit(rule.charAt(i))) {
                return i;
            }
        }
        int off = rule.indexOf("off");
        if (off >= 0) {
            return off;
        }
        int closed = rule.indexOf("closed");
        return closed >= 0 ? closed : -1;
    }

    /** {@code mo-we,fr} into the days it names, or null if anything is unrecognised. */
    private static List<DayOfWeek> parseDays(String selector) {
        List<DayOfWeek> days = new ArrayList<>();
        for (String part : selector.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int dash = token.indexOf('-');
            if (dash < 0) {
                DayOfWeek day = DAY_NAMES.get(token);
                if (day == null) {
                    return null;
                }
                days.add(day);
                continue;
            }
            DayOfWeek from = DAY_NAMES.get(token.substring(0, dash).trim());
            DayOfWeek to = DAY_NAMES.get(token.substring(dash + 1).trim());
            if (from == null || to == null) {
                return null;
            }
            // Ranges wrap, so Sa-Su and Fr-Mo both mean what they look like.
            DayOfWeek cursor = from;
            days.add(cursor);
            while (cursor != to) {
                cursor = cursor.plus(1);
                days.add(cursor);
            }
        }
        return days.isEmpty() ? null : days;
    }

    /**
     * One {@code 09:00-17:00} span, appending to the day it starts on and, when it runs past
     * midnight, to the following day as well.
     *
     * @return false if the span is not two well-formed times
     */
    private static boolean parseSpan(String span,
                                     List<OpeningHours.TimeInterval> sameDay,
                                     List<OpeningHours.TimeInterval> nextDay) {
        int dash = span.indexOf('-');
        if (dash < 0) {
            return false;
        }
        LocalTime start = parseTime(span.substring(0, dash).trim());
        LocalTime end = parseTime(span.substring(dash + 1).trim());
        if (start == null || end == null) {
            return false;
        }
        if (end.equals(start)) {
            // "09:00-09:00" is ambiguous in the wild: open a moment, or open all day.
            return false;
        }
        if (end.isBefore(start)) {
            sameDay.add(new OpeningHours.TimeInterval(start, END_OF_DAY));
            if (end.isAfter(LocalTime.MIN)) {
                nextDay.add(new OpeningHours.TimeInterval(LocalTime.MIN, end));
            }
            return true;
        }
        sameDay.add(new OpeningHours.TimeInterval(start, end));
        return true;
    }

    /** {@code 9}, {@code 09:00} or the {@code 24:00} that means the end of the day. */
    private static LocalTime parseTime(String text) {
        if (text.isEmpty()) {
            return null;
        }
        if ("24:00".equals(text)) {
            return END_OF_DAY;
        }
        int colon = text.indexOf(':');
        try {
            int hour = Integer.parseInt(colon < 0 ? text : text.substring(0, colon));
            int minute = colon < 0 ? 0 : Integer.parseInt(text.substring(colon + 1));
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return LocalTime.of(hour, minute);
        } catch (NumberFormatException notTime) {
            return null;
        }
    }
}
