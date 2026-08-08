package trippy.application;

import trippy.application.ports.ActivityRepository;
import trippy.application.ports.DistanceService;
import trippy.application.ports.ItineraryDataAccessInterface;
import trippy.application.ports.PlacesService;
import trippy.application.ports.PlacesWriter;
import trippy.application.ports.TripRepository;
import trippy.application.ports.WeatherService;
import trippy.application.scheduling.ActivityScoringPolicy;
import trippy.application.usecases.*;

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
        filterActivities = new FilterActivitiesUseCase();
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
        deleteTrip = new DeleteTripUseCase(trips);
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
