package use_case.autoschedule;

import java.util.Objects;

/** Travel duration for one directed leg, with the confidence behind it. */
public final class TravelEstimate {
    private final int minutes;
    private final TravelEstimateQuality quality;

    public TravelEstimate(int minutes, TravelEstimateQuality quality) {
        if (minutes < 0) {
            throw new IllegalArgumentException("Travel minutes cannot be negative");
        }
        if (quality == null) {
            throw new IllegalArgumentException("Travel estimate quality is required");
        }
        this.minutes = minutes;
        this.quality = quality;
    }

    /**
     * Performs the r ou te d operation.
     * @param minutes the m in ut es value
     * @return the result of the operation
     */
    public static TravelEstimate routed(int minutes) {
        return new TravelEstimate(minutes, TravelEstimateQuality.ROUTED);
    }

    /**
     * Performs the u nk no wn operation.
     * @param minutes the m in ut es value
     * @return the result of the operation
     */
    public static TravelEstimate unknown(int minutes) {
        return new TravelEstimate(minutes, TravelEstimateQuality.UNKNOWN);
    }

    public int getMinutes() {
        return minutes;
    }

    public TravelEstimateQuality getQuality() {
        return quality;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TravelEstimate)) {
            return false;
        }
        final TravelEstimate that = (TravelEstimate) other;
        return minutes == that.minutes && quality == that.quality;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minutes, quality);
    }

    @Override
    public String toString() {
        return minutes + "min(" + quality + ")";
    }
}
