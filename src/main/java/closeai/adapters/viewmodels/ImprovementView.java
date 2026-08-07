package closeai.adapters.viewmodels;

/**
 * One "Schedule improvements" card, already worded for the screen.
 *
 * <p>The Presenter has turned a {@code ScheduleImprovement} into a sentence and chosen a
 * marker by this point, so the view only stacks them. The marker is a short glyph rather
 * than a colour, because a card that can only be told apart by hue is a card some people
 * cannot tell apart at all.</p>
 */
public final class ImprovementView {

    private final String marker;
    private final String headline;
    private final String detail;

    public ImprovementView(String marker, String headline, String detail) {
        this.marker = marker == null ? "" : marker;
        this.headline = headline == null ? "" : headline;
        this.detail = detail == null ? "" : detail;
    }

    /** A short glyph identifying the category; never the only signal. */
    public String getMarker() {
        return marker;
    }

    /** The outcome in plain words, e.g. "113 min of waiting removed". */
    public String getHeadline() {
        return headline;
    }

    /** Which activity it is about, or empty when it describes the whole day. */
    public String getDetail() {
        return detail;
    }
}
