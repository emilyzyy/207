package interface_adapter.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import app.AppBuilder;
import app.AppContainer;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import entity.valueobjects.WeatherSeverity;
import interface_adapter.presenters.ManualPlanPresenter;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.SearchState;
import interface_adapter.viewmodels.SearchViewModel;

final class ManualPlanControllerTest {
    @Test
    void addsEditsAndRemovesWhileSynchronizingSharedState() {
        final AppContainer app = new AppBuilder().buildOffline();
        final Trip trip = app.createTrip.execute(
                "Toronto", LocalDate.of(2026, 8, 8), LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.WALKING);
        final java.util.List<WeatherWarning> forecast = hourlyWeather();
        final DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), Collections.emptyList(), "", false, forecast));
        final SearchViewModel search = new SearchViewModel(new SearchState(
                app.activities.findAll(), ""));
        final ManualPlanController controller = new ManualPlanController(
                app.addActivityToPlan, app.editEvent, app.removeEvent,
                trip::getId, new ManualPlanPresenter(dayPlan, search));

        controller.add("rom", "10:00");

        assertEquals(1, dayPlan.getState().getEvents().size());
        assertTrue(search.getState().getScheduledIds().contains("rom"));
        final ScheduledEvent event = dayPlan.getState().getEvents().get(0);
        assertEquals(LocalTime.of(10, 0), event.getStartTime());
        assertEquals(LocalTime.of(12, 0), event.getEndTime());
        assertSame(forecast.get(0), dayPlan.getState().getHourlyWeather().get(0),
                "adding an activity must not clear the loaded hourly forecast");

        controller.edit(event.getId(), "13:00", "15:00", "Afternoon visit");

        final ScheduledEvent edited = dayPlan.getState().getEvents().get(0);
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
        final AppContainer app = new AppBuilder().buildOffline();
        final Trip trip = app.createTrip.execute(
                "Toronto", LocalDate.of(2026, 8, 8), LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.WALKING);
        final java.util.List<WeatherWarning> forecast = hourlyWeather();
        final DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), Collections.emptyList(), "", false, forecast));
        final SearchViewModel search = new SearchViewModel(new SearchState(
                app.activities.findAll(), ""));
        final ManualPlanController controller = new ManualPlanController(
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
        final AppContainer app = new AppBuilder().buildOffline();
        final Trip trip = app.createTrip.execute(
                "Toronto", LocalDate.of(2026, 8, 8), LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.WALKING);
        final DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), Collections.emptyList(), "", false));
        final SearchViewModel search = new SearchViewModel(new SearchState(
                app.activities.findAll(), ""));
        final ManualPlanController controller = new ManualPlanController(
                app.addActivityToPlan, app.editEvent, app.removeEvent,
                trip::getId, new ManualPlanPresenter(dayPlan, search));

        controller.add("rom", LocalTime.of(10, 15), LocalTime.of(11, 0));

        final ScheduledEvent added = dayPlan.getState().getEvents().get(0);
        assertEquals(LocalTime.of(10, 15), added.getStartTime());
        assertEquals(LocalTime.of(11, 0), added.getEndTime());
    }
}
