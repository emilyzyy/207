package closeai.application;

import closeai.application.usecases.*;
import closeai.infrastructure.mock.*;
import closeai.infrastructure.persistence.InMemoryTripRepository;

public final class AppContainer {
    public final InMemoryTripRepository trips = new InMemoryTripRepository();
    public final MockPlacesService places = new MockPlacesService();
    public final MockWeatherService weather = new MockWeatherService();
    public final MockDistanceService distances = new MockDistanceService();
    public final CreateTripUseCase createTrip = new CreateTripUseCase(trips);
    public final SearchActivitiesUseCase searchActivities = new SearchActivitiesUseCase(places);
    public final BookmarkActivityUseCase bookmarkActivity = new BookmarkActivityUseCase(trips, places);
    public final RemoveBookmarkUseCase removeBookmark = new RemoveBookmarkUseCase(trips);
    public final AddActivityToPlanUseCase addActivityToPlan = new AddActivityToPlanUseCase(trips, places);
    public final AutoScheduleTripUseCase autoSchedule = new AutoScheduleTripUseCase(trips, distances, weather);
    public final EditScheduledEventUseCase editEvent = new EditScheduledEventUseCase(trips);
    public final RemoveScheduledEventUseCase removeEvent = new RemoveScheduledEventUseCase(trips);
    public final GetTripSummaryUseCase summary = new GetTripSummaryUseCase(trips);
    public final ShareTripUseCase share = new ShareTripUseCase(summary);
    public final GetWeatherWarningUseCase weatherWarning = new GetWeatherWarningUseCase(trips, weather);
}
