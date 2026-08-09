package trippy.adapters.controllers;

import trippy.AppBuilder;
import trippy.adapters.presenters.ManualPlanPresenter;
import trippy.adapters.viewmodels.DayPlanState;
import trippy.adapters.viewmodels.DayPlanViewModel;
import trippy.adapters.viewmodels.SearchState;
import trippy.adapters.viewmodels.SearchViewModel;
import trippy.application.AppContainer;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.entities.Trip;
import trippy.domain.entities.WeatherWarning;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import trippy.domain.valueobjects.WeatherSeverity;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ManualPlanControllerTest {
    @Test
    void addsEditsAndRemovesWhileSynchronizingSharedState() {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = app.createTrip.execute(
                "Toronto", LocalDate.of(2026, 8, 8), LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.WALKING);
        final java.util.List<WeatherWarning> forecast = hourlyWeather();
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), Collections.emptyList(), "", false, forecast));
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
        assertSame(forecast.get(0), dayPlan.getState().getHourlyWeather().get(0),
                "adding an activity must not clear the loaded hourly forecast");

        controller.edit(event.getId(), "13:00", "15:00", "Afternoon visit");

        ScheduledEvent edited = dayPlan.getState().getEvents().get(0);
        assertEquals(LocalTime.of(13, 0), edited.getStartTime());
        assertEquals("Afternoon visit", edited.getNotes());
        assertSame(forecast.get(0), dayPlan.getState().getHourlyWeather().get(0),
                "editing an activity must not clear the loaded hourly forecast");

        controller.remove(event.getId());

        assertTrue(dayPlan.getState().getEvents().isEmpty());
        assertFalse(search.getState().getScheduledIds().contains("rom"));
        assertSame(forecast.get(0), dayPlan.getState().getHourlyWeather().get(0),
                "removing an activity must not clear the loaded hourly forecast");
    }

    @Test
    void overlappingManualAdditionFailsWithoutChangingSavedSchedule() {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = app.createTrip.execute(
                "Toronto", LocalDate.of(2026, 8, 8), LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.WALKING);
        final java.util.List<WeatherWarning> forecast = hourlyWeather();
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), Collections.emptyList(), "", false, forecast));
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
        assertSame(forecast.get(0), dayPlan.getState().getHourlyWeather().get(0),
                "a rejected manual edit must not clear the loaded hourly forecast");
    }

    private static java.util.List<WeatherWarning> hourlyWeather() {
        return Collections.singletonList(new WeatherWarning(
                new Location(43.6532, -79.3832, "Toronto"),
                LocalTime.of(10, 0), "Clear sky", WeatherSeverity.LOW,
                "17.9 C, 0% precipitation"));
    }

    @Test
    void addsUsingTheStartAndEndChosenInThePlanningDialog() {
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

        controller.add("rom", LocalTime.of(10, 15), LocalTime.of(11, 0));

        ScheduledEvent added = dayPlan.getState().getEvents().get(0);
        assertEquals(LocalTime.of(10, 15), added.getStartTime());
        assertEquals(LocalTime.of(11, 0), added.getEndTime());
    }
}
