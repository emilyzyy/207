package interface_adapter.controllers;

import java.util.List;
import java.util.function.Supplier;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import interface_adapter.presenters.ActivityDiscoveryPresenter;
import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;
import use_case.usecases.FilterActivitiesUseCase;
import use_case.usecases.SearchActivitiesUseCase;

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
            final String currentDestination = destination.get();
            if (currentDestination == null || currentDestination.trim().isEmpty()) {
                throw new IllegalArgumentException("Create a trip before searching for activities");
            }
            final String normalizedQuery = query == null ? "" : query.trim();
            final ActivitySearchResult result = search.execute(new ActivitySearchRequest(
                    currentDestination, normalizedQuery, category, type, 100));
            final List<Activity> matches = result.getActivities();
            presenter.presentSearchResult(
                    filter.execute(matches, category, minimumRating, type),
                    normalizedQuery, category, minimumRating, type,
                    result.getFailure(), result.isPartial(), currentDestination);
        } catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        } catch (RuntimeException exception) {
            presenter.presentFailure(
                    "Something went wrong while searching. Your existing activities are still "
                            + "available.");
        }
    }
}
