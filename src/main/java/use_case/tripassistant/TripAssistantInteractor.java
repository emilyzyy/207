package use_case.tripassistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import use_case.ports.ActivityRepository;
import use_case.ports.TripAssistantGateway;
import use_case.ports.TripRepository;
import use_case.ports.WeatherService;

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
            final Trip trip = trips.findById(inputData.getTripId())
                    .orElseThrow(() -> {
                        return new IllegalArgumentException(
                                "The current trip could not be found");
                    });
            final TripAssistantRequest request = requestFor(trip, inputData);
            final TripAssistantDecision decision = gateway.answer(request);
            presenter.presentSuccess(formatter.format(request, decision));
        }
        catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        }
        catch (RuntimeException exception) {
            presenter.presentFailure("George couldn't answer right now. Please try again.");
        }
    }

    private TripAssistantRequest requestFor(Trip trip, TripAssistantInputData inputData) {
        final List<Activity> available = availableActivities(trip);
        final Set<String> bookmarkedIds = new LinkedHashSet<String>();
        for (Activity activity : trip.getBookmarkedActivities()) {
            bookmarkedIds.add(activity.getId());
        }
        List<WeatherWarning> warnings;
        try {
            warnings = weather.getHourlyWarnings(trip);
        }
        catch (RuntimeException exception) {
            warnings = Collections.emptyList();
        }
        return new TripAssistantRequest(
                trip.getDestination(), trip.getDate(), trip.getStartTime(), trip.getEndTime(),
                trip.getTransportationMode(), available, bookmarkedIds,
                trip.getScheduledEvents(), warnings, inputData.getHistory(),
                inputData.getQuestion());
    }

    private List<Activity> availableActivities(Trip trip) {
        final Map<String, Activity> known = new LinkedHashMap<String, Activity>();
        final List<Activity> discovered = trip.getDiscoveredPlaces();
        final List<Activity> base = discovered.isEmpty() ? activities.findAll() : discovered;
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
