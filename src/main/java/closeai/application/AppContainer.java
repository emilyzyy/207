package closeai.application;

import closeai.application.ports.ActivityRepository;
import closeai.application.ports.DistanceService;
import closeai.application.ports.ItineraryDataAccessInterface;
import closeai.application.ports.PlacesService;
import closeai.application.ports.PlacesWriter;
import closeai.application.ports.TripRepository;
import closeai.application.ports.WeatherService;
import closeai.application.scheduling.ActivityScoringPolicy;
import closeai.application.usecases.*;

/** Application-layer use-case registry. Concrete infrastructure is supplied by an outer builder. */
public final class AppContainer {
    public final TripRepository trips;
    public final PlacesService places;
    public final ActivityRepository activities;
    public final WeatherService weather;
    public final DistanceService distances;
    public final CreateTripUseCase createTrip;
    public final DiscoverTripPlacesUseCase discoverTripPlaces;
    public final SearchActivitiesUseCase searchActivities;
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

    public AppContainer(TripRepository trips, PlacesService places, ActivityRepository activities,
                        DistanceService distances, WeatherService weather,
                        ActivityScoringPolicy scoringPolicy) {
        this(trips, places, activities, distances, weather, scoringPolicy,
                itineraryAccessFor(trips));
    }

    public AppContainer(TripRepository trips, PlacesService places, ActivityRepository activities,
                        DistanceService distances, WeatherService weather,
                        ActivityScoringPolicy scoringPolicy,
                        ItineraryDataAccessInterface itineraries) {
        this(trips, places, activities, distances, weather, scoringPolicy, itineraries,
                placesWriterFor(activities));
    }

    public AppContainer(TripRepository trips, PlacesService places, ActivityRepository activities,
                        DistanceService distances, WeatherService weather,
                        ActivityScoringPolicy scoringPolicy,
                        ItineraryDataAccessInterface itineraries,
                        PlacesWriter placesWriter) {
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
        createTrip = new CreateTripUseCase(trips);
        discoverTripPlaces = new DiscoverTripPlacesUseCase(trips, places, placesWriter);
        searchActivities = new SearchActivitiesUseCase(places);
        bookmarkActivity = new BookmarkActivityUseCase(trips, activities);
        removeBookmark = new RemoveBookmarkUseCase(trips);
        addActivityToPlan = new AddActivityToPlanUseCase(trips, activities);
        autoSchedule = new AutoScheduleTripUseCase(trips, distances, weather, scoringPolicy);
        editItinerary = new EditItineraryInteractor(itineraries);
        editEvent = new EditScheduledEventUseCase(trips);
        removeEvent = new RemoveScheduledEventUseCase(trips);
        summary = new GetTripSummaryUseCase(trips);
        share = new ShareTripUseCase(summary);
        weatherWarning = new GetWeatherWarningUseCase(trips, weather);
        listTrips = new ListTripsUseCase(trips);
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
