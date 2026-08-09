package trippy.application.tripassistant;

import trippy.application.ports.ActivityRepository;
import trippy.application.ports.TripAssistantGateway;
import trippy.application.ports.TripRepository;
import trippy.application.ports.WeatherService;
import trippy.domain.entities.Activity;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.entities.Trip;
import trippy.domain.entities.WeatherWarning;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads the latest trip aggregate and obtains one grounded George answer. */
public final class TripAssistantInteractor implements TripAssistantInputBoundary {
    private final TripRepository trips;
    private final ActivityRepository activities;
    private final WeatherService weather;
    private final TripAssistantGateway gateway;
    private final TripAssistantOutputBoundary presenter;
    private final TripAssistantResponseFormatter formatter;

    public TripAssistantInteractor(
            TripRepository trips, ActivityRepository activities, WeatherService weather,
            TripAssistantGateway gateway, TripAssistantOutputBoundary presenter) {
        if (trips == null || activities == null || weather == null
                || gateway == null || presenter == null) {
            throw new IllegalArgumentException("Trip assistant dependencies are required");
        }
        this.trips = trips;
        this.activities = activities;
        this.weather = weather;
        this.gateway = gateway;
        this.presenter = presenter;
        formatter = new TripAssistantResponseFormatter();
    }

    @Override
    public void execute(TripAssistantInputData inputData) {
        try {
            if (inputData == null || inputData.getQuestion().isEmpty()) {
                throw new IllegalArgumentException("Type a question for George");
            }
            if (inputData.getTripId().isEmpty()) {
                throw new IllegalArgumentException("Open or create a trip before asking George");
            }
            Trip trip = trips.findById(inputData.getTripId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "The current trip could not be found"));
            TripAssistantRequest request = requestFor(trip, inputData);
            TripAssistantDecision decision = gateway.answer(request);
            presenter.presentSuccess(formatter.format(request, decision));
        } catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        } catch (RuntimeException exception) {
            presenter.presentFailure("George couldn't answer right now. Please try again.");
        }
    }

    private TripAssistantRequest requestFor(Trip trip, TripAssistantInputData inputData) {
        List<Activity> available = availableActivities(trip);
        Set<String> bookmarkedIds = new LinkedHashSet<String>();
        for (Activity activity : trip.getBookmarkedActivities()) {
            bookmarkedIds.add(activity.getId());
        }
        List<WeatherWarning> warnings;
        try {
            warnings = weather.getHourlyWarnings(trip);
        } catch (RuntimeException exception) {
            warnings = Collections.emptyList();
        }
        return new TripAssistantRequest(
                trip.getDestination(), trip.getDate(), trip.getStartTime(), trip.getEndTime(),
                trip.getTransportationMode(), available, bookmarkedIds,
                trip.getScheduledEvents(), warnings, inputData.getHistory(),
                inputData.getQuestion());
    }

    private List<Activity> availableActivities(Trip trip) {
        Map<String, Activity> known = new LinkedHashMap<String, Activity>();
        List<Activity> discovered = trip.getDiscoveredPlaces();
        List<Activity> base = discovered.isEmpty() ? activities.findAll() : discovered;
        addActivities(known, base);
        addActivities(known, trip.getBookmarkedActivities());
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getActivity() != null) {
                known.put(event.getActivity().getId(), event.getActivity());
            }
        }
        return new ArrayList<Activity>(known.values());
    }

    private void addActivities(Map<String, Activity> target, List<Activity> values) {
        for (Activity activity : values) {
            if (activity != null) {
                target.put(activity.getId(), activity);
            }
        }
    }
}
