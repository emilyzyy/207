package use_case.autoschedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import entity.entities.Activity;
import entity.valueobjects.TransportationMode;

/**
 * Direct edits to an unsaved proposal.
 *
 * <p>The traveller is reading a schedule they have decided they mostly like. Taking one
 * activity out of it is an edit to that schedule, not a request for a different one — so
 * nothing here searches, scores or re-times anything. Everything that stays keeps the time the
 * search gave it, and only the journeys either side of the removed activity change.</p>
 *
 * <p>A journey is identified by where it goes: {@code travel-<destination event id>}. That is
 * what makes "the leg into D" findable without the proposal having to carry a second
 * description of its own shape.</p>
 */
public final class ProposalDraft {

    static final String TRAVEL_ID_PREFIX = "travel-";

    private ProposalDraft() {
    }

    /** The edited proposal, or the reason it was refused. */
    public static final class Result {
        private final List<ProposedEventData> rows;
        private final String reason;
        private final int travelMinutes;
        private final int idleMinutes;
        private final int movedCount;
        private final int activityCount;

        private Result(List<ProposedEventData> rows, String reason, int travelMinutes,
                       int idleMinutes, int movedCount, int activityCount) {
            this.rows = rows;
            this.reason = reason;
            this.travelMinutes = travelMinutes;
            this.idleMinutes = idleMinutes;
            this.movedCount = movedCount;
            this.activityCount = activityCount;
        }

        static Result refused(String reason) {
            return new Result(null, reason, 0, 0, 0, 0);
        }

        public boolean isSuccessful() {
            return rows != null;
        }

        public List<ProposedEventData> getRows() {
            return rows;
        }

        public String getReason() {
            return reason;
        }

        public int getTravelMinutes() {
            return travelMinutes;
        }

        public int getIdleMinutes() {
            return idleMinutes;
        }

        public int getMovedCount() {
            return movedCount;
        }

        public int getActivityCount() {
            return activityCount;
        }
    }

    /**
     * The proposal with one activity taken out and its journeys repaired.
     *
     * @param activitiesById the places behind the proposed rows, for estimating a new leg
     */
    public static Result withoutActivity(List<ProposedEventData> proposedRows, String removeEventId,
                                         Map<String, Activity> activitiesById,
                                         TransportationMode mode, LocalDate date,
                                         TravelTimeEstimator estimator) {
        final List<ProposedEventData> activities = new ArrayList<>();
        final Map<String, ProposedEventData> legsByDestination = new LinkedHashMap<>();
        for (ProposedEventData row : proposedRows) {
            if (row.getKind() == ProposedEventData.Kind.TRAVEL) {
                if (row.getEventId().startsWith(TRAVEL_ID_PREFIX)) {
                    legsByDestination.put(
                            row.getEventId().substring(TRAVEL_ID_PREFIX.length()), row);
                }
            }
            else {
                activities.add(row);
            }
        }

        final List<ProposedEventData> remaining = new ArrayList<>();
        boolean found = false;
        for (ProposedEventData activity : activities) {
            if (activity.getEventId().equals(removeEventId)) {
                found = true;
            }
            else {
                remaining.add(activity);
            }
        }
        if (!found) {
            return Result.refused("That activity is no longer in the proposal.");
        }

        final List<ProposedEventData> rebuilt = new ArrayList<>();
        int travelMinutes = 0;
        int idleMinutes = 0;
        for (int i = 0; i < remaining.size(); i++) {
            final ProposedEventData destination = remaining.get(i);
            if (i > 0) {
                final ProposedEventData from = remaining.get(i - 1);
                ProposedEventData leg = legsByDestination.get(destination.getEventId());
                final boolean stillAdjacent = leg != null
                        && wasAdjacent(activities, from.getEventId(), destination.getEventId());
                if (!stillAdjacent) {
                    leg = estimatedLeg(from, destination, activitiesById, mode, date, estimator);
                    if (leg == null) {
                        return Result.refused("There is not enough time between "
                                + from.getTitle() + " and " + destination.getTitle()
                                + " for the journey between them. Nothing was changed — cancel "
                                + "and run Autoschedule again to re-time the day around it.");
                    }
                }
                travelMinutes += minutes(leg.getStart(), leg.getEnd());
                idleMinutes += Math.max(0, minutes(from.getEnd(), destination.getStart())
                        - minutes(leg.getStart(), leg.getEnd()));
                rebuilt.add(leg);
            }
            rebuilt.add(destination);
        }

        int moved = 0;
        for (ProposedEventData activity : remaining) {
            if (activity.isMoved()) {
                moved++;
            }
        }
        return new Result(rebuilt, "", travelMinutes, idleMinutes, moved, remaining.size());
    }

    private static boolean wasAdjacent(List<ProposedEventData> activities, String fromId,
                                       String toId) {
        for (int i = 1; i < activities.size(); i++) {
            if (activities.get(i).getEventId().equals(toId)) {
                return activities.get(i - 1).getEventId().equals(fromId);
            }
        }
        return false;
    }

    /**
     * A journey landing exactly as its activity begins, or null when one cannot honestly be
     * drawn: no estimate, a failed provider, no distance at all, or a gap too small to hold it.
     */
    private static ProposedEventData estimatedLeg(ProposedEventData from, ProposedEventData to,
                                                  Map<String, Activity> activitiesById,
                                                  TransportationMode mode, LocalDate date,
                                                  TravelTimeEstimator estimator) {
        final Activity origin = activitiesById.get(from.getEventId());
        final Activity destination = activitiesById.get(to.getEventId());
        if (estimator == null || origin == null || destination == null || date == null) {
            return null;
        }
        final int minutes;
        try {
            final TravelEstimate estimate = estimator.estimate(origin.getLocation(),
                    destination.getLocation(), mode, LocalDateTime.of(date, from.getEnd()));
            minutes = estimate == null ? 0 : estimate.getMinutes();
        }
        catch (RuntimeException providerFailed) {
            return null;
        }
        if (minutes <= 0 || minutes > minutes(from.getEnd(), to.getStart())) {
            return null;
        }
        final LocalTime departure = to.getStart().minusMinutes(minutes);
        return new ProposedEventData(TRAVEL_ID_PREFIX + to.getEventId(), "",
                "Travel to " + to.getTitle(), ProposedEventData.Kind.TRAVEL,
                departure, to.getStart(), false, false);
    }

    private static int minutes(LocalTime from, LocalTime to) {
        return (to.toSecondOfDay() - from.toSecondOfDay()) / 60;
    }
}
