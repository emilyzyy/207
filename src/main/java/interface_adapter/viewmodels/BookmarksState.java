package interface_adapter.viewmodels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import entity.entities.Activity;

/** Immutable bookmark-list display state. */
public final class BookmarksState {
    private final List<Activity> bookmarks;

    public BookmarksState(List<Activity> bookmarks) {
        this.bookmarks = Collections.unmodifiableList(new ArrayList<Activity>(
                bookmarks == null ? Collections.emptyList() : bookmarks));
    }

    public List<Activity> getBookmarks() {
        return bookmarks;
    }
}
