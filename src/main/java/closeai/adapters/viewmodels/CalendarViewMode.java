package closeai.adapters.viewmodels;

/** User-selectable calendar time scale. */
public enum CalendarViewMode {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month");

    private final String label;

    CalendarViewMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
