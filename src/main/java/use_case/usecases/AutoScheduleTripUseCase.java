package use_case.usecases;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import entity.valueobjects.EventType;
import entity.valueobjects.Location;
import entity.valueobjects.WeatherSeverity;
import use_case.ports.DistanceService;
import use_case.ports.TripRepository;
import use_case.ports.WeatherService;
import use_case.scheduling.ActivityScoringPolicy;

public final class AutoScheduleTripUseCase {
    private final TripRepository trips;
    private final DistanceService distances;
    private final WeatherService weather;
    private final ActivityScoringPolicy scoringPolicy;

    public AutoScheduleTripUseCase(TripRepository trips, DistanceService distances,
                                   WeatherService weather, ActivityScoringPolicy scoringPolicy) {
        if (trips == null || distances == null || weather == null || scoringPolicy == null) {
            throw new IllegalArgumentException("Auto-schedule dependencies are required");
        }
        this.trips = trips;
        this.distances = distances;
        this.weather = weather;
        this.scoringPolicy = scoringPolicy;
    }

    /**
     * Performs the e xe cu te operation.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    public Trip execute(String tripId) {
        final Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        final List<Activity> remaining = new ArrayList<Activity>(trip.getBookmarkedActivities());
        if (remaining.isEmpty()) {
            throw new IllegalArgumentException("Cannot auto schedule a trip with no bookmarked activities");
        }

        final List<WeatherWarning> hourlyWeather = weather.getHourlyWarnings(trip);
        validateHourlyWeather(hourlyWeather);

        final List<ScheduledEvent> schedule = new ArrayList<ScheduledEvent>();
        LocalTime cursor = trip.getStartTime();
        Location current = hourlyWeather.get(0).getLocation();
        int sequence = 0;

        while (!remaining.isEmpty()) {
            final List<CandidatePlan> feasible = new ArrayList<CandidatePlan>();
            for (Activity candidate : remaining) {
                final CandidatePlan plan = planCandidate(
                        trip, hourlyWeather, current, cursor, candidate);
                if (plan != null) {
                    feasible.add(plan);
                }
            }

            if (feasible.isEmpty()) {
                if (schedule.isEmpty()) {
                    throw new IllegalStateException("No bookmarked activity fits the trip window and opening hours");
                }
                break;
            }

            feasible.sort(Comparator.comparingDouble(CandidatePlan::getScore).reversed()
                    .thenComparing(plan -> plan.getActivity().getId()));
            final CandidatePlan chosen = feasible.get(0);

            if (chosen.getTravelMinutes() > 0) {
                schedule.add(new ScheduledEvent(eventId(trip, sequence++, EventType.TRAVEL,
                        chosen.getActivity(), cursor, chosen.getArrivalTime()), null, cursor,
                        chosen.getArrivalTime(), EventType.TRAVEL,
                        "Travel · " + chosen.getTravelMinutes() + " min"));
            }
            schedule.add(new ScheduledEvent(eventId(trip, sequence++, EventType.ACTIVITY,
                    chosen.getActivity(), chosen.getStartTime(), chosen.getEndTime()),
                    chosen.getActivity(), chosen.getStartTime(), chosen.getEndTime(),
                    EventType.ACTIVITY, "Auto scheduled"));
            cursor = chosen.getEndTime();
            current = chosen.getActivity().getLocation();
            remaining.remove(chosen.getActivity());
        }

        final Trip scheduledTrip = trip.copyWithSchedule(schedule);
        return trips.save(scheduledTrip);
    }

    private CandidatePlan planCandidate(Trip trip, List<WeatherWarning> hourlyWeather, Location current,
                                        LocalTime cursor, Activity activity) {
        final int travelMinutes = distances.estimateTravelMinutes(current, activity.getLocation(),
                trip.getTransportationMode(), LocalDateTime.of(trip.getDate(), cursor));
        if (travelMinutes < 0) {
            throw new IllegalStateException("Distance service returned negative travel time");
        }
        if (activity.getEstimatedDurationMinutes() <= 0) {
            throw new IllegalStateException("Activity duration must be positive");
        }

        final LocalTime arrival = plusWithoutDayRollover(cursor, travelMinutes);
        if (arrival == null || arrival.isAfter(trip.getEndTime())) {
            return null;
        }
        final LocalTime start = arrival.isBefore(activity.getOpeningTime())
                ? activity.getOpeningTime() : arrival;
        if (start.isBefore(trip.getStartTime()) || start.isAfter(trip.getEndTime())) {
            return null;
        }
        final LocalTime end = plusWithoutDayRollover(start, activity.getEstimatedDurationMinutes());
        if (end == null || end.isAfter(activity.getClosingTime()) || end.isAfter(trip.getEndTime())) {
            return null;
        }

        final WeatherSeverity severity = worstSeverityDuring(hourlyWeather, start, end);
        final double score = scoringPolicy.score(activity, travelMinutes, severity);
        if (!Double.isFinite(score)) {
            throw new IllegalStateException("Scoring policy returned a non-finite score");
        }
        return new CandidatePlan(activity, travelMinutes, arrival, start, end, score);
    }

    private void validateHourlyWeather(List<WeatherWarning> hourlyWeather) {
        if (hourlyWeather == null || hourlyWeather.isEmpty()) {
            throw new IllegalStateException("Weather service returned no hourly forecast");
        }
        for (WeatherWarning warning : hourlyWeather) {
            if (warning == null || warning.getLocation() == null || warning.getTime() == null
                    || warning.getSeverity() == null) {
                throw new IllegalStateException("Weather service returned an incomplete forecast");
            }
        }
    }

    /**
     * Uses the safest score for activities spanning more than one forecast hour.
     * @return the result of the operation
     */
    private WeatherSeverity worstSeverityDuring(
            List<WeatherWarning> hourlyWeather, LocalTime start, LocalTime end) {
        WeatherSeverity worst = null;
        for (WeatherWarning warning : hourlyWeather) {
            if (!hourOverlaps(warning.getTime(), start, end)) {
                continue;
            }
            if (worst == null || warning.getSeverity().ordinal() > worst.ordinal()) {
                worst = warning.getSeverity();
            }
        }
        if (worst == null) {
            throw new IllegalStateException(
                    "Weather service returned no forecast for a scheduled activity hour");
        }
        return worst;
    }

    private boolean hourOverlaps(LocalTime hour, LocalTime start, LocalTime end) {
        final LocalTime nextHour = hour.plusHours(1);
        final boolean reachesAfterStart = nextHour.isAfter(start) || nextHour.isBefore(hour);
        return hour.isBefore(end) && reachesAfterStart;
    }

    private LocalTime plusWithoutDayRollover(LocalTime time, int minutes) {
        final LocalTime result = time.plusMinutes(minutes);
        return minutes > 0 && result.isBefore(time) ? null : result;
    }

    private String eventId(Trip trip, int sequence, EventType type, Activity activity,
                           LocalTime start, LocalTime end) {
        final String seed = trip.getId() + '|' + sequence + '|' + type + '|' + activity.getId()
                + '|' + start + '|' + end;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static final class CandidatePlan {
        private final Activity activity;
        private final int travelMinutes;
        private final LocalTime arrivalTime;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final double score;

        private CandidatePlan(Activity activity, int travelMinutes, LocalTime arrivalTime,
                              LocalTime startTime, LocalTime endTime, double score) {
            this.activity = activity;
            this.travelMinutes = travelMinutes;
            this.arrivalTime = arrivalTime;
            this.startTime = startTime;
            this.endTime = endTime;
            this.score = score;
        }

        private Activity getActivity() {
            return activity;
        }

        private int getTravelMinutes() {
            return travelMinutes;
        }

        private LocalTime getArrivalTime() {
            return arrivalTime;
        }

        private LocalTime getStartTime() {
            return startTime;
        }

        private LocalTime getEndTime() {
            return endTime;
        }

        private double getScore() {
            return score;
        }
    }
}
