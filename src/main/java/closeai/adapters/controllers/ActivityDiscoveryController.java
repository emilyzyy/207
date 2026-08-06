package closeai.adapters.controllers;

import closeai.adapters.presenters.ActivityDiscoveryPresenter;
import closeai.application.usecases.FilterActivitiesUseCase;
import closeai.application.usecases.SearchActivitiesUseCase;
import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
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
            List<Activity> matches = search.execute(currentDestination, normalizedQuery);
            presenter.presentResults(
                    filter.execute(matches, category, minimumRating, type),
                    normalizedQuery, category, minimumRating, type);
        } catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        } catch (RuntimeException exception) {
            presenter.presentFailure("Activity search is temporarily unavailable");
        }
    }
}
