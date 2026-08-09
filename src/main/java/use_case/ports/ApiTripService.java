package use_case.ports;

import entity.entities.Activity;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import use_case.usecases.CreateTripInputData;
import use_case.usecases.EditItineraryInputData;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * The slice of the application the HTTP API adapter may call.
 *
 * <p>Keeps {@code interface_adapter} from reaching into the composition root: the API
 * controller depends on this port, and the application container implements it.</p>
 */
public interface ApiTripService {
    List<Activity> searchActivities(String destination, String query);

    Trip createTrip(CreateTripInputData inputData);

    Optional<Trip> findTrip(String tripId);

    Trip editItinerary(EditItineraryInputData inputData);

    Trip bookmarkActivity(String tripId, String activityId);

    Trip removeBookmark(String tripId, String activityId);

    Trip addActivityToPlan(String tripId, String activityId, LocalTime startTime);

    Trip autoSchedule(String tripId);

    Trip removeEvent(String tripId, String eventId);

    Trip editEvent(String tripId, String eventId, LocalTime startTime, LocalTime endTime, String notes);

    String tripSummary(String tripId);

    String shareTrip(String tripId);

    WeatherWarning weatherWarning(String tripId);

    List<WeatherWarning> hourlyWeather(String tripId);

    /** Place storage for discovered places, or {@code null} when unavailable. */
    PlacesWriter cachedPlaces();
}
