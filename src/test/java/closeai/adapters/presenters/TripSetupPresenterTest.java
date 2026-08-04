package closeai.adapters.presenters;

import closeai.adapters.viewmodels.BookmarksState;
import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.SearchState;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.adapters.viewmodels.TripOptionsState;
import closeai.adapters.viewmodels.TripOptionsViewModel;
import closeai.application.usecases.TripSetupOutputData;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TripSetupPresenterTest {

    @Test
    void successPublishesTheSameActiveTripAcrossViewModels() {
        DashboardViewModel dashboard = new DashboardViewModel(
                new DashboardState("", null, "", ""));
        SearchViewModel search = new SearchViewModel(
                new SearchState(Collections.emptyList(), ""));
        BookmarksViewModel bookmarks = new BookmarksViewModel(
                new BookmarksState(Collections.emptyList()));
        DayPlanViewModel dayPlan = new DayPlanViewModel(
                new DayPlanState("", Collections.emptyList(), "", false));
        TripOptionsViewModel options = new TripOptionsViewModel(
                new TripOptionsState("", null, null, null));
        TripSetupPresenter presenter = new TripSetupPresenter(
                dashboard, search, bookmarks, dayPlan, options, null, null);
        Trip trip = new Trip(
                "trip-1", "Montreal", LocalDate.of(2026, 8, 2),
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                TransportationMode.TRANSIT);

        presenter.presentSuccess(new TripSetupOutputData(trip, true));

        assertEquals("trip-1", dayPlan.getState().getTripId());
        assertEquals("trip-1", options.getState().getTripId());
        assertEquals("Montreal", dashboard.getState().getDestination());
        assertEquals("Trip created successfully.", options.getState().getMessage());
        assertFalse(options.getState().isError());
    }

    @Test
    void failureOnlyChangesTripSetupFeedback() {
        TripOptionsViewModel options = new TripOptionsViewModel(
                new TripOptionsState("", LocalDate.now(), LocalTime.NOON,
                        LocalTime.of(18, 0)));
        TripSetupPresenter presenter = new TripSetupPresenter(
                new DashboardViewModel(new DashboardState("", null, "", "")),
                new SearchViewModel(new SearchState(Collections.emptyList(), "")),
                new BookmarksViewModel(new BookmarksState(Collections.emptyList())),
                new DayPlanViewModel(
                        new DayPlanState("", Collections.emptyList(), "", false)),
                options, null, null);

        presenter.presentFailure("Destination is required");

        assertEquals("Destination is required", options.getState().getMessage());
        assertTrue(options.getState().isError());
        assertEquals("", options.getState().getTripId());
    }
}
