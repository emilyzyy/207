package closeai.application;

import closeai.application.ports.ActivityRepository;
import closeai.application.ports.DistanceService;
import closeai.application.ports.PlacesService;
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
    public final SearchActivitiesUseCase searchActivities;
    public final BookmarkActivityUseCase bookmarkActivity;
    public final RemoveBookmarkUseCase removeBookmark;
    public final AddActivityToPlanUseCase addActivityToPlan;
    public final AutoScheduleTripUseCase autoSchedule;
    public final EditScheduledEventUseCase editEvent;
    public final RemoveScheduledEventUseCase removeEvent;
    public final GetTripSummaryUseCase summary;
    public final ShareTripUseCase share;
    public final GetWeatherWarningUseCase weatherWarning;

    public AppContainer(TripRepository trips, PlacesService places, ActivityRepository activities,
                        DistanceService distances, WeatherService weather,
                        ActivityScoringPolicy scoringPolicy) {
        if (trips == null || places == null || activities == null || distances == null
                || weather == null || scoringPolicy == null) {
            throw new IllegalArgumentException("Application dependencies are required");
        }
        this.trips = trips;
        this.places = places;
        this.activities = activities;
        this.weather = weather;
        this.distances = distances;
        createTrip = new CreateTripUseCase(trips);
        searchActivities = new SearchActivitiesUseCase(places);
        bookmarkActivity = new BookmarkActivityUseCase(trips, activities);
        removeBookmark = new RemoveBookmarkUseCase(trips);
        addActivityToPlan = new AddActivityToPlanUseCase(trips, activities);
        autoSchedule = new AutoScheduleTripUseCase(trips, distances, weather, scoringPolicy);
        editEvent = new EditScheduledEventUseCase(trips);
        removeEvent = new RemoveScheduledEventUseCase(trips);
        summary = new GetTripSummaryUseCase(trips);
        share = new ShareTripUseCase(summary);
        weatherWarning = new GetWeatherWarningUseCase(trips, weather);
    }
}
