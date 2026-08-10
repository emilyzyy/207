package interface_adapter.viewmodels;

/**
 * A small chip naming a requirement the schedule actively worked around.
 *
 * <p>Deliberately not an improvement. A tile says the day got better by a measurable amount;
 * a chip says something the traveller asked for was honoured even though it constrained the
 * answer. They are ranked, coloured and sized differently because they are different claims.</p>
 *
 * <p>Only constraints that <em>affected placement</em> earn one. "Opening hours respected" for
 * a venue that was open all day is not a fact about this schedule, it is a fact about the
 * venue, and printing it teaches the traveller to ignore the row.</p>
 */
public final class ConstraintChipView {

    private final String marker;
    private final String label;

    public ConstraintChipView(String marker, String label) {
        this.marker = marker == null ? "" : marker;
        this.label = label == null ? "" : label;
    }

    public String getMarker() {
        return marker;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return marker.isEmpty() ? label : marker + " " + label;
    }
}
