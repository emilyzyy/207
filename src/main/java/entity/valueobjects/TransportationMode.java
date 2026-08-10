package entity.valueobjects;

/**
 * How the traveller gets between activities.
 *
 * <p>{@link #FASTEST} is a request rather than a means of transport: it asks for whichever
 * of the real modes is quickest on each leg. It exists so the traveller can decline to
 * choose, without the application quietly choosing for them.</p>
 */
public enum TransportationMode {
    WALKING("Walking"),
    DRIVING("Driving"),
    TRANSIT("Transit"),
    FASTEST("Fastest available");

    private final String label;

    TransportationMode(String label) {
        this.label = label;
    }

    /**
     * Title case for the interface; {@code name()} stays the stored form.
     * @return the result of the operation
     */
    public String getLabel() {
        return label;
    }
    /**
     * True for a real means of transport, false for {@link #FASTEST}.
     * @return the result of the operation
     */

    public boolean isSpecific() {
        return this != FASTEST;
    }
    /**
     * The real modes a route can actually be planned for, in no significant order.
     * @return the result of the operation
     */

    public static TransportationMode[] specificModes() {
        return new TransportationMode[] {WALKING, DRIVING, TRANSIT};
    }
}
