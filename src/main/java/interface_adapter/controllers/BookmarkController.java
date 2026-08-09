package interface_adapter.controllers;

import interface_adapter.presenters.ActivityDiscoveryPresenter;
import interface_adapter.viewmodels.SearchViewModel;
import use_case.usecases.BookmarkActivityUseCase;
import use_case.usecases.RemoveBookmarkUseCase;
import entity.entities.Trip;
import java.util.function.Supplier;

/** Handles bookmark and unbookmark actions without exposing repositories to Swing. */
public final class BookmarkController {
    private final BookmarkActivityUseCase bookmark;
    private final RemoveBookmarkUseCase remove;
    private final Supplier<String> tripId;
    private final SearchViewModel search;
    private final ActivityDiscoveryPresenter presenter;

    public BookmarkController(BookmarkActivityUseCase bookmark, RemoveBookmarkUseCase remove,
                              Supplier<String> tripId, SearchViewModel search,
                              ActivityDiscoveryPresenter presenter) {
        if (bookmark == null || remove == null || tripId == null
                || search == null || presenter == null) {
            throw new IllegalArgumentException("Bookmark dependencies are required");
        }
        this.bookmark = bookmark;
        this.remove = remove;
        this.tripId = tripId;
        this.search = search;
        this.presenter = presenter;
    }

    public void toggle(String activityId) {
        execute(activityId, search.getState().getBookmarkedIds().contains(activityId));
    }

    public void remove(String activityId) {
        execute(activityId, true);
    }

    private void execute(String activityId, boolean removing) {
        try {
            String currentTripId = tripId.get();
            if (currentTripId == null || currentTripId.trim().isEmpty()) {
                throw new IllegalArgumentException("Create a trip before bookmarking activities");
            }
            Trip updated = removing
                    ? remove.execute(currentTripId, activityId)
                    : bookmark.execute(currentTripId, activityId);
            presenter.presentTrip(updated);
        } catch (IllegalArgumentException exception) {
            presenter.presentFailure(exception.getMessage());
        }
    }
}
