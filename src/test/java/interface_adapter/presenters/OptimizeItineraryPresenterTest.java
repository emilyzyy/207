package interface_adapter.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import entity.valueobjects.WeatherSeverity;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import use_case.usecases.OptimizeItineraryOutputData;

final class OptimizeItineraryPresenterTest {

    @Test
    void successAndFailureUpdateTheSharedDayPlanState() {
        final DayPlanViewModel viewModel = new DayPlanViewModel(
                new DayPlanState("trip-1", Collections.emptyList(), "", false));
        final AtomicInteger stateChanges = new AtomicInteger();
        viewModel.addPropertyChangeListener(event -> stateChanges.incrementAndGet());
        final OptimizeItineraryPresenter presenter = new OptimizeItineraryPresenter(viewModel);
        final WeatherWarning hourly = new WeatherWarning(
                new Location(43.65, -79.38, "Toronto"), LocalTime.of(10, 0),
                "Rain", WeatherSeverity.MEDIUM, "18°C");
        viewModel.setState(new DayPlanState(
                "trip-1", Collections.emptyList(), "", false,
                Collections.singletonList(hourly)));
        stateChanges.set(0);
        final Trip trip = tripWithOneEvent();

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
        final Trip trip = new Trip("trip-1", "Toronto", LocalDate.of(2026, 7, 23),
                LocalTime.of(9, 0), LocalTime.of(18, 0), TransportationMode.WALKING);
        final Activity activity = new Activity("rom", "Royal Ontario Museum",
                ActivityCategory.MUSEUM, new Location(43.67, -79.39, "100 Queens Park"),
                4.7, 120, LocalTime.of(9, 0), LocalTime.of(20, 0),
                IndoorOutdoorType.INDOOR, "Low");
        trip.addEvent(new ScheduledEvent("event-rom", activity,
                LocalTime.of(10, 0), LocalTime.of(12, 0),
                EventType.ACTIVITY, "Museum visit"));
        return trip;
    }
}
