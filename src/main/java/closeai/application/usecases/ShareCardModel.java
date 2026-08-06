package closeai.application.usecases;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable display model for an itinerary PNG share card. */
public final class ShareCardModel {
    private final String destination;
    private final LocalDate date;
    private final String transportationMode;
    private final List<ShareCardLine> lines;

    public ShareCardModel(String destination, LocalDate date, String transportationMode,
                          List<ShareCardLine> lines) {
        if (destination == null || destination.trim().isEmpty()) {
            throw new IllegalArgumentException("Destination is required");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date is required");
        }
        if (transportationMode == null || transportationMode.trim().isEmpty()) {
            throw new IllegalArgumentException("Transportation mode is required");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Share card requires at least one event line");
        }
        this.destination = destination.trim();
        this.date = date;
        this.transportationMode = transportationMode.trim();
        this.lines = Collections.unmodifiableList(new ArrayList<ShareCardLine>(lines));
    }

    public String getDestination() {
        return destination;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getTransportationMode() {
        return transportationMode;
    }

    public List<ShareCardLine> getLines() {
        return lines;
    }

    /** One timed row on the share card. */
    public static final class ShareCardLine {
        private final String timeRange;
        private final String title;
        private final boolean travel;

        public ShareCardLine(String timeRange, String title, boolean travel) {
            if (timeRange == null || timeRange.trim().isEmpty()) {
                throw new IllegalArgumentException("Time range is required");
            }
            if (title == null || title.trim().isEmpty()) {
                throw new IllegalArgumentException("Title is required");
            }
            this.timeRange = timeRange.trim();
            this.title = title.trim();
            this.travel = travel;
        }

        public String getTimeRange() {
            return timeRange;
        }

        public String getTitle() {
            return title;
        }

        public boolean isTravel() {
            return travel;
        }
    }
}
