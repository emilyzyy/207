package entity.entities;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import entity.valueobjects.TransportationMode;

/**
 * A trip, made of one or more consecutive days.
 *
 * <p>Existing code that never heard of multi-day trips keeps working: the single-day
 * constructor and the day-0 accessors ({@link #getDate()}, {@link #getStartTime()},
 * {@link #getEndTime()}, {@link #getScheduledEvents()}) all read the first day, so a
 * one-day trip behaves exactly as it always did. Code that needs a specific day uses
 * {@link #getDay(int)} or {@link #getDays()}.</p>
 *
 * <p>Scheduling engines can stay single-day by being handed a per-day projection
 * ({@link #projectDay(int)}), which is how the untouched Autoschedule engine is run once
 * per day.</p>
 */
public final class Trip {
    private final String id;
    private String destination;
    private TransportationMode transportationMode;
    private final List<TripDay> days = new ArrayList<TripDay>();
    private int activeDayIndex;
    private final List<Activity> bookmarkedActivities = new ArrayList<Activity>();
    private final List<Activity> discoveredPlaces = new ArrayList<Activity>();

    public Trip(String id, String destination, LocalDate date, LocalTime startTime,
                LocalTime endTime, TransportationMode transportationMode) {
        this(id, destination, transportationMode,
                Collections.singletonList(new TripDay(date, startTime, endTime)));
    }

    /**
     * A multi-day trip. Days must be non-empty and consecutive by date.
     * @param transportationMode the t ra ns po rt at io nm od e value
     * @param destination the d es ti na ti on value
     * @param id the i d value
     */
    public Trip(String id, String destination, TransportationMode transportationMode,
                List<TripDay> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("A trip needs at least one day");
        }
        this.id = id;
        this.destination = destination;
        this.transportationMode = transportationMode;
        LocalDate previous = null;
        for (TripDay day : days) {
            if (day == null) {
                throw new IllegalArgumentException("Trip days cannot contain null");
            }
            if (previous != null && !day.getDate().equals(previous.plusDays(1))) {
                throw new IllegalArgumentException("Trip days must be consecutive");
            }
            previous = day.getDate();
            this.days.add(day);
        }
        this.activeDayIndex = 0;
    }

    public String getId() {
        return id;
    }

    public String getDestination() {
        return destination;
    }

    public TransportationMode getTransportationMode() {
        return transportationMode;
    }

    public List<Activity> getBookmarkedActivities() {
        return Collections.unmodifiableList(bookmarkedActivities);
    }

    public List<Activity> getDiscoveredPlaces() {
        return Collections.unmodifiableList(discoveredPlaces);
    }
    /**
     * The active day's date, for callers that predate multi-day trips.
     * @return the result of the operation
     */

    public LocalDate getDate() {
        return days.get(activeDayIndex).getDate();
    }
    /**
     * The active day's start, for callers that predate multi-day trips.
     * @return the result of the operation
     */

    public LocalTime getStartTime() {
        return days.get(activeDayIndex).getStartTime();
    }
    /**
     * The active day's end, for callers that predate multi-day trips.
     * @return the result of the operation
     */

    public LocalTime getEndTime() {
        return days.get(activeDayIndex).getEndTime();
    }
    /**
     * The active day's events, for callers that predate multi-day trips.
     * @return the result of the operation
     */

    public List<ScheduledEvent> getScheduledEvents() {
        return days.get(activeDayIndex).getScheduledEvents();
    }

    public List<TripDay> getDays() {
        return Collections.unmodifiableList(days);
    }

    public int getDayCount() {
        return days.size();
    }

    /**
     * Performs the g et da y operation.
     * @param index the i nd ex value
     * @return the result of the operation
     */
    public TripDay getDay(int index) {
        return days.get(index);
    }

    public int getActiveDayIndex() {
        return activeDayIndex;
    }

    /**
     * Performs the s et ac ti ve da yi nd ex operation.
     * @param index the i nd ex value
     */
    public void setActiveDayIndex(int index) {
        if (index < 0 || index >= days.size()) {
            throw new IllegalArgumentException("Day index out of range");
        }
        activeDayIndex = index;
    }

    /**
     * All trip dates, in order.
     * @return the result of the operation
     */
    public List<LocalDate> getTripDates() {
        final List<LocalDate> dates = new ArrayList<LocalDate>();
        for (TripDay day : days) {
            dates.add(day.getDate());
        }
        return Collections.unmodifiableList(dates);
    }

    /**
     * Replaces the pool of places discovered for this trip's destination.
     * @param places the p la ce s value
     */
    public void setDiscoveredPlaces(List<Activity> places) {
        discoveredPlaces.clear();
        if (places != null) {
            for (Activity activity : places) {
                if (activity != null) {
                    discoveredPlaces.add(activity);
                }
            }
        }
    }

    /**
     * Applies shared trip options across every day: the same window for all days, dates
     * made consecutive from the given start date.
      * @param end the e nd value
      * @param start the s ta rt value
      * @param date the d at e value
      * @param destination the d es ti na ti on value
     */
    public void updateOptions(String destination, LocalDate date, LocalTime start, LocalTime end,
                              TransportationMode mode) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Trip end must follow start");
        }
        if (destination != null) {
            this.destination = destination.trim();
        }
        if (mode != null) {
            this.transportationMode = mode;
        }
        for (int i = 0; i < days.size(); i++) {
            final LocalDate dayDate = date == null ? days.get(i).getDate() : date.plusDays(i);
            days.get(i).updateWindow(dayDate, start, end);
        }
        if (activeDayIndex >= days.size()) {
            activeDayIndex = 0;
        }
    }

    /** Updates shared options while reconciling events with a narrower day window. */
    public void updateOptionsPreservingSchedule(
            String destination, LocalDate date, LocalTime start, LocalTime end,
            TransportationMode mode) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Trip end must follow start");
        }
        if (destination != null) {
            this.destination = destination.trim();
        }
        if (mode != null) {
            this.transportationMode = mode;
        }
        for (int i = 0; i < days.size(); i++) {
            final LocalDate dayDate = date == null ? days.get(i).getDate() : date.plusDays(i);
            days.get(i).updateWindowPreservingSchedule(dayDate, start, end);
        }
    }

    /**
     * Performs the b oo km ar k operation.
     * @param activity the a ct iv it y value
     */
    public void bookmark(Activity activity) {
        for (Activity item : bookmarkedActivities) {
            if (item.getId().equals(activity.getId())) {
                return;
            }
        }
        bookmarkedActivities.add(activity);
    }

    /**
     * Performs the r em ov eb oo km ar k operation.
     * @param activityId the a ct iv it yi d value
     */
    public void removeBookmark(String activityId) {
        bookmarkedActivities.removeIf(activity -> activity.getId().equals(activityId));
    }
    /**
     * Adds an event to the active day.
     * @param event the e ve nt value
     */

    public void addEvent(ScheduledEvent event) {
        days.get(activeDayIndex).addEvent(event);
    }
    /**
     * Replaces the active day's schedule.
     * @param events the e ve nt s value
     */

    public void replaceSchedule(List<ScheduledEvent> events) {
        days.get(activeDayIndex).replaceSchedule(events);
    }
    /**
     * Replaces a specific day's schedule, leaving the other days untouched.
     * @param events the e ve nt s value
     * @param dayIndex the d ay in de x value
     */

    public void replaceDaySchedule(int dayIndex, List<ScheduledEvent> events) {
        days.get(dayIndex).replaceSchedule(events);
    }
    /**
     * Returns a separate aggregate so scheduling failures never partially mutate this trip.
     * @param events the e ve nt s value
     * @return the result of the operation
     */

    public Trip copyWithSchedule(List<ScheduledEvent> events) {
        final Trip copy = new Trip(id, destination, transportationMode, copyDays());
        for (Activity activity : bookmarkedActivities) {
            copy.bookmark(activity);
        }
        copy.setDiscoveredPlaces(discoveredPlaces);
        copy.replaceDaySchedule(activeDayIndex, events);
        copy.activeDayIndex = activeDayIndex;
        return copy;
    }

    /**
     * A single-day trip whose window and events are the given day's. Bookmarks and
     * discovered places are shared so a projected save never loses trip-level data.
      * @param dayIndex the d ay in de x value
      * @return the result of the operation
     */
    public Trip projectDay(int dayIndex) {
        final TripDay source = days.get(dayIndex);
        final TripDay projected = new TripDay(source.getDate(), source.getStartTime(), source.getEndTime());
        projected.replaceSchedule(source.getScheduledEvents());
        final Trip projection = new Trip(id, destination, transportationMode,
                Collections.singletonList(projected));
        for (Activity activity : bookmarkedActivities) {
            projection.bookmark(activity);
        }
        projection.setDiscoveredPlaces(discoveredPlaces);
        return projection;
    }

    /**
     * Performs the r em ov ee ve nt operation.
     * @param eventId the e ve nt id value
     */
    public void removeEvent(String eventId) {
        days.get(activeDayIndex).removeEvent(eventId);
    }

    /**
     * Performs the f in de ve nt operation.
     * @param eventId the e ve nt id value
     * @return the result of the operation
     */
    public ScheduledEvent findEvent(String eventId) {
        return days.get(activeDayIndex).findEvent(eventId);
    }

    private List<TripDay> copyDays() {
        final List<TripDay> copies = new ArrayList<TripDay>();
        for (TripDay day : days) {
            final TripDay copy = new TripDay(day.getDate(), day.getStartTime(), day.getEndTime());
            copy.replaceSchedule(day.getScheduledEvents());
            copies.add(copy);
        }
        return copies;
    }
}
