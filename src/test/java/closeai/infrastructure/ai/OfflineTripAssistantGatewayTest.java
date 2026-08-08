package closeai.infrastructure.ai;

import closeai.application.tripassistant.TripAssistantDecision;
import closeai.application.tripassistant.TripAssistantMessage;
import closeai.application.tripassistant.TripAssistantRequest;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OfflineTripAssistantGatewayTest {
    private final OfflineTripAssistantGateway gateway = new OfflineTripAssistantGateway();

    @Test
    void rainRecommendationsUseOnlyExistingShelteredActivities() {
        TripAssistantDecision decision = gateway.answer(request(
                "What should I do if it rains?", Collections.emptyList()));

        assertEquals(TripAssistantDecision.Intent.RAIN, decision.getIntent());
        assertTrue(decision.getActivityIds().contains("museum"));
        assertTrue(decision.getActivityIds().contains("tower"));
        assertFalse(decision.getActivityIds().contains("park"));
    }

    @Test
    void bookmarkQuestionRestrictsSelectionToBookmarks() {
        TripAssistantDecision decision = gateway.answer(request(
                "Which bookmarked activity should I visit?", Collections.emptyList()));

        assertEquals(TripAssistantDecision.Intent.BOOKMARKS, decision.getIntent());
        assertEquals(Collections.singletonList("museum"), decision.getActivityIds());
    }

    @Test
    void whyQuestionReusesTheLastGroundedRecommendations() {
        List<TripAssistantMessage> history = Collections.singletonList(
                new TripAssistantMessage(TripAssistantMessage.Role.ASSISTANT,
                        "Try the park", Collections.singletonList("park")));

        TripAssistantDecision decision = gateway.answer(request(
                "Why is this a good choice?", history));

        assertEquals(TripAssistantDecision.Intent.EXPLAIN, decision.getIntent());
        assertEquals(Collections.singletonList("park"), decision.getActivityIds());
    }

    @Test
    void afternoonRecommendationFitsTheActualFreeGapInTheDayPlan() {
        Activity longVisit = activity(
                "long", IndoorOutdoorType.INDOOR, 5.0, 120);
        Activity shortVisit = activity(
                "short", IndoorOutdoorType.INDOOR, 4.0, 45);
        Activity booked = activity(
                "booked", IndoorOutdoorType.INDOOR, 4.5, 240);
        ScheduledEvent occupied = new ScheduledEvent(
                "occupied", booked, LocalTime.of(12, 0), LocalTime.of(16, 0),
                EventType.ACTIVITY, "Already planned");
        TripAssistantRequest request = new TripAssistantRequest(
                "Toronto", LocalDate.of(2026, 8, 20), LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.WALKING,
                Arrays.asList(longVisit, shortVisit, booked), Collections.emptySet(),
                Collections.singletonList(occupied), Collections.emptyList(),
                Collections.emptyList(), "What fits into my afternoon?");

        TripAssistantDecision decision = gateway.answer(request);

        assertTrue(decision.getActivityIds().contains("short"));
        assertFalse(decision.getActivityIds().contains("long"));
    }

    private TripAssistantRequest request(
            String question, List<TripAssistantMessage> history) {
        return new TripAssistantRequest(
                "Toronto", LocalDate.of(2026, 8, 20), LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.WALKING,
                Arrays.asList(
                        activity("museum", IndoorOutdoorType.INDOOR, 4.8),
                        activity("tower", IndoorOutdoorType.MIXED, 4.7),
                        activity("park", IndoorOutdoorType.OUTDOOR, 4.9)),
                new HashSet<String>(Collections.singletonList("museum")),
                Collections.emptyList(), Collections.emptyList(), history, question);
    }

    private Activity activity(String id, IndoorOutdoorType type, double rating) {
        return activity(id, type, rating, 60);
    }

    private Activity activity(
            String id, IndoorOutdoorType type, double rating, int duration) {
        return new Activity(id, id, ActivityCategory.ATTRACTION,
                new Location(43.6, -79.3, id), rating, duration,
                LocalTime.of(9, 0), LocalTime.of(20, 0), type, "Low");
    }
}
