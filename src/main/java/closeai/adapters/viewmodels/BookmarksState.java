package closeai.adapters.viewmodels;

import closeai.domain.entities.Activity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
