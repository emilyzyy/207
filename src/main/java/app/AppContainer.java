package app;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import entity.entities.Activity;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import use_case.ports.AccountService;
import use_case.ports.ActivityRepository;
import use_case.ports.ApiTripService;
import use_case.ports.DistanceService;
import use_case.ports.ItineraryDataAccessInterface;
import use_case.ports.PlacesService;
import use_case.ports.PlacesWriter;
import use_case.ports.TripRepository;
import use_case.ports.WeatherService;
import use_case.scheduling.ActivityScoringPolicy;
import use_case.usecases.*;

/**
 * Application-layer use-case registry. Concrete infrastructure is supplied by an outer builder.
 *
 * <p>Implements {@link ApiTripService} so the HTTP adapter can call the application through a
 * port instead of reaching into the composition root.</p>
 */
public final class AppContainer implements ApiTripService {
    public final TripRepository trips;
    public final PlacesService places;
    public final ActivityRepository activities;
    public final WeatherService weather;
    public final DistanceService distances;
    /** Present when Supabase account features (profile / friends) are enabled; otherwise null. */
    public final AccountService account;
    public final CreateTripUseCase createTrip;
    public final DiscoverTripPlacesUseCase discoverTripPlaces;
    public final SearchActivitiesInteractor searchActivities;
    public final FilterActivitiesUseCase filterActivities;
    public final BookmarkActivityUseCase bookmarkActivity;
    public final RemoveBookmarkUseCase removeBookmark;
    public final AddActivityToPlanUseCase addActivityToPlan;
    public final AutoScheduleTripUseCase autoSchedule;
    public final EditItineraryInputBoundary editItinerary;
    public final EditScheduledEventUseCase editEvent;
    public final RemoveScheduledEventUseCase removeEvent;
    public final GetTripSummaryUseCase summary;
    public final ShareTripInputBoundary share;
    public final GetWeatherWarningUseCase weatherWarning;
    public final ListTripsUseCase listTrips;
    public final DeleteTripUseCase deleteTrip;

    public AppContainer(TripRepository trips, PlacesService places, ActivityRepository activities,
                        DistanceService distances, WeatherService weather,
                        ActivityScoringPolicy scoringPolicy) {
        this(trips, places, activities, distances, weather, scoringPolicy,
                itineraryAccessFor(trips), placesWriterFor(activities), null);
    }

    public AppContainer(TripRepository trips, PlacesService places, ActivityRepository activities,
                        DistanceService distances, WeatherService weather,
                        ActivityScoringPolicy scoringPolicy,
                        ItineraryDataAccessInterface itineraries) {
        this(trips, places, activities, distances, weather, scoringPolicy, itineraries,
                placesWriterFor(activities), null);
    }

    public AppContainer(TripRepository trips, PlacesService places, ActivityRepository activities,
                        DistanceService distances, WeatherService weather,
                        ActivityScoringPolicy scoringPolicy,
                        ItineraryDataAccessInterface itineraries,
                        PlacesWriter placesWriter) {
        this(trips, places, activities, distances, weather, scoringPolicy, itineraries,
                placesWriter, null);
    }

    public AppContainer(TripRepository trips, PlacesService places, ActivityRepository activities,
                        DistanceService distances, WeatherService weather,
                        ActivityScoringPolicy scoringPolicy,
                        ItineraryDataAccessInterface itineraries,
                        PlacesWriter placesWriter,
                        AccountService account) {
        if (trips == null || places == null || activities == null || distances == null
                || weather == null || scoringPolicy == null || itineraries == null
                || placesWriter == null) {
            throw new IllegalArgumentException("Application dependencies are required");
        }
        this.trips = trips;
        this.places = places;
        this.activities = activities;
        this.weather = weather;
        this.distances = distances;
        this.account = account;
        createTrip = new CreateTripUseCase(trips);
        discoverTripPlaces = new DiscoverTripPlacesUseCase(trips, places, placesWriter);
        searchActivities = new SearchActivitiesInteractor(places);
        filterActivities = new FilterActivitiesUseCase();
        bookmarkActivity = new BookmarkActivityUseCase(trips, activities);
        removeBookmark = new RemoveBookmarkUseCase(trips);
        addActivityToPlan = new AddActivityToPlanUseCase(trips, activities);
        autoSchedule = new AutoScheduleTripUseCase(trips, distances, weather, scoringPolicy);
        editItinerary = new EditItineraryInteractor(itineraries);
        editEvent = new EditScheduledEventUseCase(trips);
        removeEvent = new RemoveScheduledEventUseCase(trips, distances);
        summary = new GetTripSummaryUseCase(trips);
        share = new ShareTripUseCase(summary);
        weatherWarning = new GetWeatherWarningUseCase(trips, weather);
        listTrips = new ListTripsUseCase(trips);
        deleteTrip = new DeleteTripUseCase(trips);
    }

    @Override
    public List<Activity> searchActivities(String destination, String query) {
        return searchActivities.execute(new use_case.search.ActivitySearchRequest(
                destination, query, null, null, 100)).getActivities();
    }

    @Override
    public Trip createTrip(CreateTripInputData inputData) {
        return createTrip.execute(inputData);
    }

    @Override
    public Optional<Trip> findTrip(String tripId) {
        return trips.findById(tripId);
    }

    @Override
    public Trip editItinerary(EditItineraryInputData inputData) {
        return editItinerary.execute(inputData);
    }

    @Override
    public Trip bookmarkActivity(String tripId, String activityId) {
        return bookmarkActivity.execute(tripId, activityId);
    }

    @Override
    public Trip removeBookmark(String tripId, String activityId) {
        return removeBookmark.execute(tripId, activityId);
    }

    @Override
    public Trip addActivityToPlan(String tripId, String activityId, LocalTime startTime) {
        return addActivityToPlan.execute(tripId, activityId, startTime);
    }

    @Override
    public Trip autoSchedule(String tripId) {
        return autoSchedule.execute(tripId);
    }

    @Override
    public Trip removeEvent(String tripId, String eventId) {
        return removeEvent.execute(tripId, eventId);
    }

    @Override
    public Trip editEvent(String tripId, String eventId, LocalTime startTime,
                          LocalTime endTime, String notes) {
        return editEvent.execute(tripId, eventId, startTime, endTime, notes);
    }

    @Override
    public String tripSummary(String tripId) {
        return summary.execute(tripId);
    }

    @Override
    public String shareTrip(String tripId) {
        return share.execute(tripId);
    }

    @Override
    public WeatherWarning weatherWarning(String tripId) {
        return weatherWarning.execute(tripId);
    }

    @Override
    public List<WeatherWarning> hourlyWeather(String tripId) {
        return weatherWarning.executeHourly(tripId);
    }

    @Override
    public PlacesWriter cachedPlaces() {
        return activities instanceof PlacesWriter ? (PlacesWriter) activities : null;
    }

    private static ItineraryDataAccessInterface itineraryAccessFor(TripRepository trips) {
        if (trips instanceof ItineraryDataAccessInterface) {
            return (ItineraryDataAccessInterface) trips;
        }
        return new TripRepositoryItineraryDataAccess(trips);
    }

    private static PlacesWriter placesWriterFor(ActivityRepository activities) {
        if (!(activities instanceof PlacesWriter)) {
            throw new IllegalArgumentException(
                    "ActivityRepository must also implement PlacesWriter for place discovery");
        }
        return (PlacesWriter) activities;
    }
}
