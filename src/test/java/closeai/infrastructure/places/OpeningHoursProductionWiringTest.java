package closeai.infrastructure.places;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.adapters.controllers.AutoScheduleController;
import closeai.adapters.controllers.AutoScheduleSettings;
import closeai.adapters.controllers.TaskRunner;
import closeai.adapters.gateways.DistanceServiceTravelTimeEstimator;
import closeai.adapters.presenters.AutoSchedulePresenter;
import closeai.adapters.viewmodels.AutoScheduleStatus;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.PreviewRowView;
import closeai.application.autoschedule.AutoScheduleInteractor;
import closeai.application.autoschedule.WeatherContext;
import closeai.application.autoschedule.engine.ScheduleEngine;
import closeai.application.autoschedule.policy.DaylightPolicy;
import closeai.application.autoschedule.policy.MealWindowPolicy;
import closeai.application.autoschedule.policy.WeatherSuitabilityPolicy;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.TransportationMode;
import closeai.infrastructure.mock.MockDistanceService;
import closeai.infrastructure.persistence.InMemoryTripRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A place discovered from OpenStreetMap, all the way to a scheduled time.
 *
 * <p>Every other opening-hours test builds its activity by hand, which proves the rules but
 * not the plumbing. This one starts where the application really starts — an Overpass
 * response over a real local HTTP server — runs it through Raashid's
 * {@link NominatimPlacesService}, drops the resulting {@link Activity} into a Day Plan, and
 * asks the production Interactor to schedule it. If any link in that chain drops the hours,
 * these tests fail and the rest of the suite would not notice.</p>
 *
 * <p>The trip date is Wednesday 12 August 2026 throughout, so the weekday in each tag is
 * visible in the test rather than hidden in a calendar lookup.</p>
 */
class OpeningHoursProductionWiringTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** One place, with whatever opening_hours tag the test wants to try. */
    private Activity discover(String openingHoursTag) throws IOException {
        String tag = openingHoursTag == null ? ""
                : ",\"opening_hours\":\"" + openingHoursTag + "\"";
        String overpass = "{\"elements\":[{\"id\":900,\"lat\":43.66,\"lon\":-79.39,"
                + "\"tags\":{\"name\":\"City Museum\",\"tourism\":\"museum\"" + tag + "}}]}";

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> respond(exchange,
                "[{\"lat\":\"43.65\",\"lon\":\"-79.38\"}]"));
        server.createContext("/interpreter", exchange -> respond(exchange, overpass));
        server.start();

        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        List<Activity> found = new NominatimPlacesService(HttpClient.newHttpClient(),
                new ObjectMapper(), URI.create(base + "/search"),
                URI.create(base + "/interpreter")).search("Toronto", "");
        assertEquals(1, found.size(), "the stub server should have yielded one place");
        return found.get(0);
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * Puts the discovered place in a Day Plan and runs the real use case over it.
     *
     * <p>The starting time has to sit inside the activity's flattened window, because
     * {@code Trip} refuses to hold an event outside it. That coarse guard is the entity's,
     * and it is all the entity can know; the per-weekday reading is the scheduler's, and it
     * is stricter. Every test below starts somewhere the entity accepts and then checks the
     * schedule applied the stricter rule.</p>
     */
    private DayPlanState schedule(Activity discovered, LocalTime startAt, int minutes) {
        Trip trip = new Trip("trip-1", "Toronto", WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
        List<ScheduledEvent> events = new ArrayList<>();
        events.add(new ScheduledEvent("event-museum", discovered, startAt,
                startAt.plusMinutes(minutes), EventType.ACTIVITY, ""));
        trip.replaceSchedule(events);

        InMemoryTripRepository trips = new InMemoryTripRepository();
        trips.save(trip);
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        AutoScheduleInteractor interactor = new AutoScheduleInteractor(trips,
                new DistanceServiceTravelTimeEstimator(new MockDistanceService()),
                anyTrip -> WeatherContext.unavailable(),
                new AutoSchedulePresenter(viewModel),
                Arrays.asList(new WeatherSuitabilityPolicy(), new MealWindowPolicy(),
                        new DaylightPolicy()),
                new ScheduleEngine());
        new AutoScheduleController(interactor, viewModel, TaskRunner.immediate())
                .preview(new AutoScheduleSettings(LocalTime.of(9, 0), LocalTime.of(21, 0),
                        TransportationMode.WALKING, Collections.emptyList(), true, false));
        return viewModel.getState();
    }

    private static PreviewRowView rowFor(DayPlanState state, String title) {
        for (PreviewRowView row : state.getPreviewRows()) {
            if (title.equals(row.getTitle())) {
                return row;
            }
        }
        return null;
    }

    private static String hoursWarning(DayPlanState state) {
        for (String warning : state.getWarnings()) {
            if (warning.contains("Flexible timing")) {
                return warning;
            }
        }
        return null;
    }

    @Test
    void hoursFromTheProviderReachTheScheduleAndMoveTheVisit() throws Exception {
        // Open 13:00-18:00; the traveller left it at 17:00 with nothing else in the day.
        DayPlanState state = schedule(discover("Mo-Fr 13:00-18:00"), LocalTime.of(17, 0), 60);

        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(), state.getMessage());
        PreviewRowView museum = rowFor(state, "City Museum");
        assertNotNull(museum, state.getPreviewRows().toString());
        assertEquals(LocalTime.of(13, 0), museum.getStart(),
                "the day is free from 9am, so the earliest lawful start is the moment the "
                        + "doors open -- which OpenStreetMap says is 1pm, not 9am");
        assertFalse(museum.getEnd().isAfter(LocalTime.of(18, 0)));
        assertNull(hoursWarning(state), "these hours were known, so nothing was guessed");
    }

    @Test
    void aMiddayClosureFromTheProviderIsRespectedEndToEnd() throws Exception {
        // Two shifts. A two-hour visit cannot fit the first, so it must land in the second.
        // Flattened, this reads as 10:00-19:00, so the entity is happy to hold a visit that
        // runs straight through the closure. The scheduler must not be.
        DayPlanState state = schedule(discover("We 10:00-11:30,15:00-19:00"),
                LocalTime.of(10, 30), 120);

        PreviewRowView museum = rowFor(state, "City Museum");
        assertNotNull(museum, state.getMessage());
        assertEquals(LocalTime.of(15, 0), museum.getStart(),
                "a two-hour visit does not fit the 90-minute morning shift, and the gap "
                        + "between the shifts is not open");
    }

    @Test
    void aVenueClosedOnTheTripDateIsRefusedByName() throws Exception {
        // Saturdays only, and the trip is a Wednesday.
        DayPlanState state = schedule(discover("Sa 10:00-16:00"), LocalTime.of(10, 0), 60);

        assertEquals(AutoScheduleStatus.CONFLICT, state.getStatus(), state.getMessage());
        assertTrue(state.getMessage().contains("City Museum"), state.getMessage());
    }

    @Test
    void aPlaceWithNoHoursTagIsStillScheduledAndTheGuessIsStated() throws Exception {
        DayPlanState state = schedule(discover(null), LocalTime.of(10, 0), 60);

        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(), state.getMessage());
        assertNotNull(rowFor(state, "City Museum"),
                "most OpenStreetMap places have no hours; refusing them all is not an option");
        String warning = hoursWarning(state);
        assertNotNull(warning, state.getWarnings().toString());
        assertTrue(warning.contains("City Museum"), warning);
    }

    @Test
    void anUnreadableTagIsTreatedAsNoTagRatherThanAsAClosedDoor() throws Exception {
        DayPlanState state = schedule(discover("Mo-Su sunrise-sunset"),
                LocalTime.of(10, 0), 60);

        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(), state.getMessage());
        assertNotNull(hoursWarning(state),
                "a tag we cannot read is a guess like any other, and must be declared");
    }

    /**
     * The one that would have gone wrong. Raashid's flattened window reads this tag as
     * 09:00-23:00, because 09:00 is the earliest opening anywhere in the week. On a Wednesday
     * the venue actually shuts at 17:00, and the schedule must believe the weekday, not the
     * flattened span.
     */
    @Test
    void theScheduleBelievesTheWeekdayRatherThanTheFlattenedWeek() throws Exception {
        Activity discovered = discover("Mo-Fr 09:00-17:00; Sa-Su 11:00-23:00");
        assertEquals(LocalTime.of(23, 0), discovered.getClosingTime(),
                "precondition: the flattened window really does say 23:00");

        DayPlanState state = schedule(discovered, LocalTime.of(19, 0), 120);

        PreviewRowView museum = rowFor(state, "City Museum");
        assertNotNull(museum, state.getMessage());
        assertFalse(museum.getEnd().isAfter(LocalTime.of(17, 0)),
                "Wednesday closing is 17:00; the flattened 23:00 must not be believed, but "
                        + "the visit ended at " + museum.getEnd());
    }
}
