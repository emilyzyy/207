package interface_adapter.viewmodels;

/**
 * One thing the proposed schedule provably achieved, already worded for a small tile.
 *
 * <p>Split into a number and a label rather than a sentence because the tile is read at a
 * glance and in a grid: "19 MIN / less travel" tells a traveller what changed before they
 * have finished looking at it, where "19 min less travel than your current order" is a line
 * of prose that has to be read. The View does no wording of its own — everything here is
 * decided by the Presenter from the use case's output.</p>
 *
 * <p>{@code secondary} is optional and often empty. A tile whose supporting line merely
 * restates its own heading is worse than a tile with no supporting line at all.</p>
 */
public final class ImprovementView {

    private final String marker;
    private final String primary;
    private final String secondary;

    public ImprovementView(String marker, String primary, String secondary) {
        this.marker = marker == null ? "" : marker;
        this.primary = primary == null ? "" : primary;
        this.secondary = secondary == null ? "" : secondary;
    }

    /** A small glyph, so the category survives being printed in grey or read aloud. */
    public String getMarker() {
        return marker;
    }

    /** The figure or short title: "19 MIN", "PIN KEPT", "DAYLIGHT". */
    public String getPrimary() {
        return primary;
    }

    /** One short supporting line, or empty when the primary says it all. */
    public String getSecondary() {
        return secondary;
    }

    /** The whole card as one sentence, for tooltips and screen readers. */
    public String spoken() {
        return secondary.isEmpty() ? primary : primary + ", " + secondary;
    }

    @Override
    public String toString() {
        return spoken();
    }
}
