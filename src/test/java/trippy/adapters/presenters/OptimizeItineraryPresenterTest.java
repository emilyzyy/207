package trippy.adapters.presenters;

import trippy.adapters.viewmodels.DayPlanState;
import trippy.adapters.viewmodels.DayPlanViewModel;
import trippy.application.usecases.OptimizeItineraryOutputData;
import trippy.domain.entities.Activity;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.entities.Trip;
import trippy.domain.entities.WeatherWarning;
import trippy.domain.valueobjects.ActivityCategory;
import trippy.domain.valueobjects.EventType;
import trippy.domain.valueobjects.IndoorOutdoorType;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import trippy.domain.valueobjects.WeatherSeverity;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OptimizeItineraryPresenterTest {

    @Test
    void successAndFailureUpdateTheSharedDayPlanState() {
        DayPlanViewModel viewModel = new DayPlanViewModel(
                new DayPlanState("trip-1", Collections.emptyList(), "", false));
        AtomicInteger stateChanges = new AtomicInteger();
        viewModel.addPropertyChangeListener(event -> stateChanges.incrementAndGet());
        OptimizeItineraryPresenter presenter = new OptimizeItineraryPresenter(viewModel);
        WeatherWarning hourly = new WeatherWarning(
                new Location(43.65, -79.38, "Toronto"), LocalTime.of(10, 0),
                "Rain", WeatherSeverity.MEDIUM, "18°C");
        viewModel.setState(new DayPlanState(
                "trip-1", Collections.emptyList(), "", false,
                Collections.singletonList(hourly)));
        stateChanges.set(0);
        Trip trip = tripWithOneEvent();

        presenter.presentSuccess(new OptimizeItineraryOutputData(trip, "Compacted"));

        assertEquals(trip.getScheduledEvents(), viewModel.getState().getEvents());
        assertEquals("Compacted", viewModel.getState().getMessage());
        assertFalse(viewModel.getState().isError());
        assertEquals(Collections.singletonList(hourly),
                viewModel.getState().getHourlyWeather());

        presenter.presentFailure("Cannot compact");

        assertEquals(trip.getScheduledEvents(), viewModel.getState().getEvents());
        assertEquals("Cannot compact", viewModel.getState().getMessage());
        assertTrue(viewModel.getState().isError());
        assertEquals(2, stateChanges.get());
    }

    private Trip tripWithOneEvent() {
        Trip trip = new Trip("trip-1", "Toronto", LocalDate.of(2026, 7, 23),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
        Activity activity = new Activity("rom", "Royal Ontario Museum",
                ActivityCategory.MUSEUM, new Location(43.67, -79.39, "100 Queens Park"),
                4.7, 120, LocalTime.of(9, 0), LocalTime.of(20, 0),
                IndoorOutdoorType.INDOOR, "Low");
        trip.addEvent(new ScheduledEvent("event-rom", activity,
                LocalTime.of(10, 0), LocalTime.of(12, 0),
                EventType.ACTIVITY, "Museum visit"));
        return trip;
    }
}
