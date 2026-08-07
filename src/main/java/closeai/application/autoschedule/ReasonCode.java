package closeai.application.autoschedule;

/**
 * Structured explanations produced by the engine and the policies.
 *
 * <p>Codes travel outward and the Presenter turns them into sentences. Assembling user
 * prose inside the engine or the Interactor would put display wording in the layers
 * least able to change it, which is how explanations decay into hard-coded strings.</p>
 */
public enum ReasonCode {
    /** Placed when it was, because the venue closes soon after. */
    CLOSING_SOON,
    /** Could not start earlier because the venue was not yet open. */
    OPENS_LATER,
    /** Held at the exact time the user locked. */
    LOCKED_BY_USER,
    /** Moved clear of a period the user marked unavailable. */
    AVOIDS_UNAVAILABLE_PERIOD,
    /** Falls inside a customary meal window. */
    IN_MEAL_WINDOW,
    /** Outside customary meal windows, because nothing better fitted. */
    OUTSIDE_MEAL_WINDOW,
    /** Outdoor activity placed in daylight. */
    IN_DAYLIGHT,
    /** Outdoor activity that could not be placed in daylight. */
    OUTSIDE_DAYLIGHT,
    /** Outdoor exposure during poorer forecast conditions. */
    WEATHER_EXPOSURE
}
