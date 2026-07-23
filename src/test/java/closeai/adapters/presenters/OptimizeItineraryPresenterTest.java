package closeai.adapters.presenters;

import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.application.usecases.OptimizeItineraryOutputData;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
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
        Trip trip = tripWithOneEvent();

        presenter.presentSuccess(new OptimizeItineraryOutputData(trip, "Compacted"));

        assertEquals(trip.getScheduledEvents(), viewModel.getState().getEvents());
        assertEquals("Compacted", viewModel.getState().getMessage());
        assertFalse(viewModel.getState().isError());

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
