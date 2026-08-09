package trippy.application.usecases;

import trippy.application.ports.DistanceService;
import trippy.application.ports.TripRepository;
import trippy.application.ports.WeatherService;
import trippy.application.scheduling.ActivityScoringPolicy;
import trippy.domain.entities.Activity;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.entities.Trip;
import trippy.domain.entities.WeatherWarning;
import trippy.domain.valueobjects.EventType;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.WeatherSeverity;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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

    public Trip execute(String tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        List<Activity> remaining = new ArrayList<Activity>(trip.getBookmarkedActivities());
        if (remaining.isEmpty()) {
            throw new IllegalArgumentException("Cannot auto schedule a trip with no bookmarked activities");
        }

        List<WeatherWarning> hourlyWeather = weather.getHourlyWarnings(trip);
        validateHourlyWeather(hourlyWeather);

        List<ScheduledEvent> schedule = new ArrayList<ScheduledEvent>();
        LocalTime cursor = trip.getStartTime();
        Location current = hourlyWeather.get(0).getLocation();
        int sequence = 0;

        while (!remaining.isEmpty()) {
            List<CandidatePlan> feasible = new ArrayList<CandidatePlan>();
            for (Activity candidate : remaining) {
                CandidatePlan plan = planCandidate(
                        trip, hourlyWeather, current, cursor, candidate);
                if (plan != null) feasible.add(plan);
            }

            if (feasible.isEmpty()) {
                if (schedule.isEmpty()) {
                    throw new IllegalStateException("No bookmarked activity fits the trip window and opening hours");
                }
                break;
            }

            feasible.sort(Comparator.comparingDouble(CandidatePlan::getScore).reversed()
                    .thenComparing(plan -> plan.getActivity().getId()));
            CandidatePlan chosen = feasible.get(0);

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

        Trip scheduledTrip = trip.copyWithSchedule(schedule);
        return trips.save(scheduledTrip);
    }

    private CandidatePlan planCandidate(Trip trip, List<WeatherWarning> hourlyWeather, Location current,
                                        LocalTime cursor, Activity activity) {
        int travelMinutes = distances.estimateTravelMinutes(current, activity.getLocation(),
                trip.getTransportationMode(), LocalDateTime.of(trip.getDate(), cursor));
        if (travelMinutes < 0) {
            throw new IllegalStateException("Distance service returned negative travel time");
        }
        if (activity.getEstimatedDurationMinutes() <= 0) {
            throw new IllegalStateException("Activity duration must be positive");
        }

        LocalTime arrival = plusWithoutDayRollover(cursor, travelMinutes);
        if (arrival == null || arrival.isAfter(trip.getEndTime())) return null;
        LocalTime start = arrival.isBefore(activity.getOpeningTime())
                ? activity.getOpeningTime() : arrival;
        if (start.isBefore(trip.getStartTime()) || start.isAfter(trip.getEndTime())) return null;
        LocalTime end = plusWithoutDayRollover(start, activity.getEstimatedDurationMinutes());
        if (end == null || end.isAfter(activity.getClosingTime()) || end.isAfter(trip.getEndTime())) return null;

        WeatherSeverity severity = worstSeverityDuring(hourlyWeather, start, end);
        double score = scoringPolicy.score(activity, travelMinutes, severity);
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

    /** Uses the safest score for activities spanning more than one forecast hour. */
    private WeatherSeverity worstSeverityDuring(
            List<WeatherWarning> hourlyWeather, LocalTime start, LocalTime end) {
        WeatherSeverity worst = null;
        for (WeatherWarning warning : hourlyWeather) {
            if (!hourOverlaps(warning.getTime(), start, end)) continue;
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
        LocalTime nextHour = hour.plusHours(1);
        boolean reachesAfterStart = nextHour.isAfter(start) || nextHour.isBefore(hour);
        return hour.isBefore(end) && reachesAfterStart;
    }

    private LocalTime plusWithoutDayRollover(LocalTime time, int minutes) {
        LocalTime result = time.plusMinutes(minutes);
        return minutes > 0 && result.isBefore(time) ? null : result;
    }

    private String eventId(Trip trip, int sequence, EventType type, Activity activity,
                           LocalTime start, LocalTime end) {
        String seed = trip.getId() + '|' + sequence + '|' + type + '|' + activity.getId()
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

        private Activity getActivity() { return activity; }
        private int getTravelMinutes() { return travelMinutes; }
        private LocalTime getArrivalTime() { return arrivalTime; }
        private LocalTime getStartTime() { return startTime; }
        private LocalTime getEndTime() { return endTime; }
        private double getScore() { return score; }
    }
}
