package closeai.adapters.controllers;

import closeai.AppBuilder;
import closeai.adapters.presenters.ManualPlanPresenter;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.application.AppContainer;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ManualPlanControllerTest {
    @Test
    void addsEditsAndRemovesWhileSynchronizingSharedState() {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = app.createTrip.execute(
                "Toronto", LocalDate.of(2026, 8, 8), LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.WALKING);
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), Collections.emptyList(), "", false));
        SearchViewModel search = new SearchViewModel(new SearchState(
                app.activities.findAll(), ""));
        ManualPlanController controller = new ManualPlanController(
                app.addActivityToPlan, app.editEvent, app.removeEvent,
                trip::getId, new ManualPlanPresenter(dayPlan, search));

        controller.add("rom", "10:00");

        assertEquals(1, dayPlan.getState().getEvents().size());
        assertTrue(search.getState().getScheduledIds().contains("rom"));
        ScheduledEvent event = dayPlan.getState().getEvents().get(0);
        assertEquals(LocalTime.of(10, 0), event.getStartTime());
        assertEquals(LocalTime.of(12, 0), event.getEndTime());

        controller.edit(event.getId(), "13:00", "15:00", "Afternoon visit");

        ScheduledEvent edited = dayPlan.getState().getEvents().get(0);
        assertEquals(LocalTime.of(13, 0), edited.getStartTime());
        assertEquals("Afternoon visit", edited.getNotes());

        controller.remove(event.getId());

        assertTrue(dayPlan.getState().getEvents().isEmpty());
        assertFalse(search.getState().getScheduledIds().contains("rom"));
    }

    @Test
    void overlappingManualAdditionFailsWithoutChangingSavedSchedule() {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = app.createTrip.execute(
                "Toronto", LocalDate.of(2026, 8, 8), LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.WALKING);
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), Collections.emptyList(), "", false));
        SearchViewModel search = new SearchViewModel(new SearchState(
                app.activities.findAll(), ""));
        ManualPlanController controller = new ManualPlanController(
                app.addActivityToPlan, app.editEvent, app.removeEvent,
                trip::getId, new ManualPlanPresenter(dayPlan, search));
        controller.add("rom", "10:00");

        controller.add("pai", "11:00");

        assertTrue(dayPlan.getState().isError());
        assertEquals(1, app.trips.findById(trip.getId()).orElseThrow()
                .getScheduledEvents().size());
    }
}
