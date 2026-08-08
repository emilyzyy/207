package trippy.application.autoschedule;

/**
 * The kinds of improvement a schedule can be shown to have made.
 *
 * <p>Every one of these is a <em>comparison</em> between the day as it stood and the day as
 * proposed. That is the whole point of the type existing: a final-state observation such as
 * "this activity is in daylight" says nothing about whether scheduling helped, because it
 * may have been in daylight all along. Only a change earns a card.</p>
 */
public enum ScheduleImprovementType {
    /** Avoidable waiting fell between the current plan and the proposal. */
    WAITING_REDUCED,
    /** Total travel fell between the current plan and the proposal. */
    TRAVEL_REDUCED,
    /** A pinned activity is still at the exact time it was pinned to. */
    LOCK_PRESERVED,
    /** An outdoor activity that was outside daylight is now inside it. */
    MOVED_INTO_DAYLIGHT,
    /** A weather-sensitive activity now sits at an hour with a milder forecast. */
    MOVED_TO_BETTER_WEATHER,
    /** A meal that sat outside its customary window now sits closer to or inside it. */
    MEAL_MOVED_TOWARD_WINDOW,
    /** The activities appear in the same relative order they were given in. */
    ORDER_PRESERVED
}
