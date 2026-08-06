package closeai.application.autoschedule;

/**
 * The soft considerations a user may switch off before generating a Preview.
 *
 * <p>Hard constraints and travel minimisation are deliberately absent: they are not
 * negotiable, and travel minimisation is what keeps "better" meaningful when every
 * optional preference is disabled.</p>
 *
 * <p>The first three are implemented as {@code SoftPolicy} strategies scoring one
 * placement at a time. The last two are whole-schedule score tiers, because idle time
 * and order disruption are properties of a complete day rather than of any single
 * activity; forcing them into the per-placement interface would be a pattern applied
 * for its own sake.</p>
 */
public enum PolicyId {
    WEATHER,
    MEAL_TIME,
    DAYLIGHT,
    REDUCE_IDLE,
    PRESERVE_ORDER
}
