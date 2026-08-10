package app;

import app.AppContainer;
import entity.entities.Activity;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Composition-root seeding of demo bookmarks and a fixed day plan for a freshly created trip. */
final class DemoSeeding {
    private static final LocalTime[] SLOTS = {
            LocalTime.of(10, 0), LocalTime.of(12, 45), LocalTime.of(15, 0) };

    private DemoSeeding() {
    }

    static void bookmarkAndSchedule(AppContainer app, String tripId, List<Activity> places,
                                    int scheduleCount, int bookmarkCount) {
        Set<String> scheduledIds = new HashSet<>();
        int added = 0;
        for (Activity activity : places) {
            if (added >= scheduleCount) break;
            try {
                app.addActivityToPlan.execute(tripId, activity.getId(), SLOTS[added]);
                scheduledIds.add(activity.getId());
                added++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        int bookmarked = 0;
        for (Activity activity : places) {
            if (bookmarked >= bookmarkCount) break;
            if (scheduledIds.contains(activity.getId())) continue;
            try {
                app.bookmarkActivity.execute(tripId, activity.getId());
                bookmarked++;
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
