package interface_adapter.controllers;

import interface_adapter.presenters.ActivityDiscoveryPresenter;
import use_case.usecases.FilterActivitiesUseCase;
import use_case.usecases.SearchActivitiesUseCase;
import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;
import use_case.search.SearchFailure;
import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import java.util.List;
import java.util.function.Supplier;

/** Converts Swing search/filter values into application use-case calls. */
public final class ActivityDiscoveryController {
    private final SearchActivitiesUseCase search;
    private final FilterActivitiesUseCase filter;
    private final Supplier<String> destination;
    private final ActivityDiscoveryPresenter presenter;

    public ActivityDiscoveryController(SearchActivitiesUseCase search,
                                       FilterActivitiesUseCase filter,
                                       Supplier<String> destination,
                                       ActivityDiscoveryPresenter presenter) {
        if (search == null || filter == null || destination == null || presenter == null) {
            throw new IllegalArgumentException("Activity discovery dependencies are required");
        }
        this.search = search;
        this.filter = filter;
        this.destination = destination;
        this.presenter = presenter;
    }

    public void execute(String query, ActivityCategory category, double minimumRating,
                        IndoorOutdoorType type) {
        try {
            String currentDestination = destination.get();
            if (currentDestination == null || currentDestination.trim().isEmpty()) {
                throw new IllegalArgumentException("Create a trip before searching for activities");
            }
            String normalizedQuery = query == null ? "" : query.trim();
            ActivitySearchResult result = search.execute(new ActivitySearchRequest(
                    currentDestination, normalizedQuery, category, type, 100));
            List<Activity> matches = result.getActivities();
            presenter.presentResults(
                    filter.execute(matches, category, minimumRating, type),
                    normalizedQuery, category, minimumRating, type,
                    feedback(result.getFailure(), result.isPartial()));
        } catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        } catch (RuntimeException exception) {
            presenter.presentFailure("Activity search is temporarily unavailable");
        }
    }

    private static String feedback(SearchFailure failure, boolean partial) {
        if (failure == null || failure == SearchFailure.NONE || failure == SearchFailure.NO_MATCH) {
            return "";
        }
        String message;
        switch (failure) {
            case INVALID_DESTINATION:
                message = "The trip destination could not be located.";
                break;
            case RATE_LIMITED:
                message = "OpenStreetMap is busy. Please try again shortly.";
                break;
            default:
                message = "OpenStreetMap is temporarily unavailable.";
                break;
        }
        return partial ? "Showing cached results. " + message : message;
    }
}
