package closeai.adapters.viewmodels;

/**
 * Where the Day Plan is in the Autoschedule interaction.
 *
 * <p>Modelling this explicitly keeps the view from having to infer its situation from
 * combinations of empty lists and message strings, and makes "the itinerary has not
 * changed yet" a state the UI can render rather than an assumption.</p>
 */
public enum AutoScheduleStatus {
    /** Nothing in progress; the Day Plan shows the real itinerary. */
    IDLE,
    /** Working: routing, forecast and search are running away from the screen. */
    LOADING,
    /** A proposal is on screen. The real itinerary is still untouched. */
    PREVIEW,
    /** The proposal has been saved and is now the itinerary. */
    APPLIED,
    /** The day genuinely cannot be arranged; the reason names what blocked it. */
    CONFLICT,
    /** The request could not be carried out, for example an unusable setting. */
    FAILURE
}
