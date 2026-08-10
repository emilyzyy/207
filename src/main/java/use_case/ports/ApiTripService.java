package use_case.ports;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import entity.entities.Activity;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import use_case.usecases.CreateTripInputData;
import use_case.usecases.EditItineraryInputData;

/**
 * The slice of the application the HTTP API adapter may call.
 *
 * <p>Keeps {@code interface_adapter} from reaching into the composition root: the API
 * controller depends on this port, and the application container implements it.</p>
 */
public interface ApiTripService {
    /**
     * Performs the s ea rc ha ct iv it ie s operation.
     * @param query the q ue ry value
     * @param destination the d es ti na ti on value
     * @return the result of the operation
     */
    List<Activity> searchActivities(String destination, String query);

    /**
     * Performs the c re at et ri p operation.
     * @param inputData the i np ut da ta value
     * @return the result of the operation
     */
    Trip createTrip(CreateTripInputData inputData);

    /**
     * Performs the f in dt ri p operation.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    Optional<Trip> findTrip(String tripId);

    /**
     * Performs the e di ti ti ne ra ry operation.
     * @param inputData the i np ut da ta value
     * @return the result of the operation
     */
    Trip editItinerary(EditItineraryInputData inputData);

    /**
     * Performs the b oo km ar ka ct iv it y operation.
     * @param activityId the a ct iv it yi d value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    Trip bookmarkActivity(String tripId, String activityId);

    /**
     * Performs the r em ov eb oo km ar k operation.
     * @param activityId the a ct iv it yi d value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    Trip removeBookmark(String tripId, String activityId);

    /**
     * Performs the a dd ac ti vi ty to pl an operation.
     * @param activityId the a ct iv it yi d value
     * @param startTime the s ta rt ti me value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    Trip addActivityToPlan(String tripId, String activityId, LocalTime startTime);

    /**
     * Performs the a ut os ch ed ul e operation.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    Trip autoSchedule(String tripId);

    /**
     * Performs the r em ov ee ve nt operation.
     * @param eventId the e ve nt id value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    Trip removeEvent(String tripId, String eventId);

    /**
     * Performs the e di te ve nt operation.
     * @param startTime the s ta rt ti me value
     * @param endTime the e nd ti me value
     * @param notes the n ot es value
     * @param eventId the e ve nt id value
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    Trip editEvent(String tripId, String eventId, LocalTime startTime, LocalTime endTime, String notes);

    /**
     * Performs the t ri ps um ma ry operation.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    String tripSummary(String tripId);

    /**
     * Performs the s ha re tr ip operation.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    String shareTrip(String tripId);

    /**
     * Performs the w ea th er wa rn in g operation.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    WeatherWarning weatherWarning(String tripId);

    /**
     * Performs the h ou rl yw ea th er operation.
     * @param tripId the t ri pi d value
     * @return the result of the operation
     */
    List<WeatherWarning> hourlyWeather(String tripId);

    /**
     * Place storage for discovered places, or {@code null} when unavailable.
     * @return the result of the operation
     */
    PlacesWriter cachedPlaces();
}
