package closeai.application.usecases;

import closeai.application.ports.DistanceService;
import closeai.application.ports.TripRepository;
import closeai.application.ports.WeatherService;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.WeatherSeverity;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class AutoScheduleTripUseCase {
    private final TripRepository trips;
    private final DistanceService distances;
    private final WeatherService weather;
    public AutoScheduleTripUseCase(TripRepository trips, DistanceService distances, WeatherService weather) {
        this.trips = trips; this.distances = distances; this.weather = weather;
    }
    public Trip execute(String tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        WeatherWarning warning = weather.getWarning(trip);
        List<Activity> remaining = new ArrayList<Activity>(trip.getBookmarkedActivities());
        if (remaining.isEmpty()) throw new IllegalArgumentException("Bookmark activities before auto scheduling");
        List<ScheduledEvent> schedule = new ArrayList<ScheduledEvent>();
        LocalTime cursor = trip.getStartTime();
        Location current = new Location(43.6532, -79.3832, trip.getDestination());

        while (!remaining.isEmpty()) {
            final Location origin = current;
            remaining.sort(Comparator.comparingDouble((Activity activity) -> score(activity,
                    distances.estimateTravelMinutes(origin, activity.getLocation(), trip.getTransportationMode()), warning)).reversed());
            Activity chosen = null;
            int travel = 0;
            for (Activity candidate : remaining) {
                int candidateTravel = schedule.isEmpty() ? 0 : distances.estimateTravelMinutes(current,
                        candidate.getLocation(), trip.getTransportationMode());
                LocalTime start = cursor.plusMinutes(candidateTravel);
                LocalTime end = start.plusMinutes(candidate.getEstimatedDurationMinutes());
                if (!start.isBefore(candidate.getOpeningTime()) && !end.isAfter(candidate.getClosingTime())
                        && !end.isAfter(trip.getEndTime())) { chosen = candidate; travel = candidateTravel; break; }
            }
            if (chosen == null) break;
            if (travel > 0) {
                LocalTime travelEnd = cursor.plusMinutes(travel);
                schedule.add(new ScheduledEvent(UUID.randomUUID().toString(), null, cursor, travelEnd,
                        EventType.TRAVEL, "Travel · " + travel + " min"));
                cursor = travelEnd;
            }
            LocalTime end = cursor.plusMinutes(chosen.getEstimatedDurationMinutes());
            schedule.add(new ScheduledEvent(UUID.randomUUID().toString(), chosen, cursor, end,
                    EventType.ACTIVITY, "Auto scheduled"));
            cursor = end;
            current = chosen.getLocation();
            remaining.remove(chosen);
        }
        trip.replaceSchedule(schedule);
        return trips.save(trip);
    }
    private double score(Activity activity, int travelMinutes, WeatherWarning warning) {
        double weatherPenalty = activity.getIndoorOutdoorType() == IndoorOutdoorType.OUTDOOR
                ? (warning.getSeverity() == WeatherSeverity.HIGH ? 4.0 : warning.getSeverity() == WeatherSeverity.MEDIUM ? 2.0 : 0.4) : 0.0;
        return activity.getRating() * 2.0 - travelMinutes / 20.0 - weatherPenalty;
    }
}
