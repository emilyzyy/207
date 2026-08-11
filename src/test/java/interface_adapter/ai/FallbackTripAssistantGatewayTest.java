package interface_adapter.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import entity.valueobjects.TripAssistantMessage;
import use_case.tripassistant.TripAssistantDecision;
import use_case.tripassistant.TripAssistantRequest;

final class FallbackTripAssistantGatewayTest {

    @Test
    void unknownLiveActivityTriggersOfflineGroundedFallback() {
        final FallbackTripAssistantGateway gateway = new FallbackTripAssistantGateway(
                ignored -> {
                    return new TripAssistantDecision(
                            TripAssistantDecision.Intent.RECOMMEND,
                            Collections.singletonList("invented"));
                },
                new OfflineTripAssistantGateway());

        final TripAssistantDecision decision = gateway.answer(request());

        assertEquals(Collections.singletonList("real"), decision.getActivityIds());
        assertTrue(decision.getNotice().contains("offline"));
    }

    @Test
    void groundedGeneralAnswerUsesLiveAi() {
        final FallbackTripAssistantGateway gateway = new FallbackTripAssistantGateway(
                ignored -> {
                    return new TripAssistantDecision(
                            TripAssistantDecision.Intent.GENERAL, Collections.emptyList(),
                            "I'm George, and 3 + 3 is 6.", "");
                },
                new OfflineTripAssistantGateway());

        final TripAssistantDecision decision = gateway.answer(request(
                "What is your name, and what is 3 + 3?"));

        assertEquals("I'm George, and 3 + 3 is 6.", decision.getAnswer());
        assertTrue(decision.getActivityIds().isEmpty());
        assertTrue(decision.getNotice().isEmpty());
    }

    @Test
    void liveFailurePreservesOfflineConversationalAnswer() {
        final FallbackTripAssistantGateway gateway = new FallbackTripAssistantGateway(
                ignored -> {
                    throw new IllegalStateException("offline test");
                },
                new OfflineTripAssistantGateway());
        final TripAssistantRequest request = request("What is your name?");

        final TripAssistantDecision decision = gateway.answer(request);

        assertEquals(TripAssistantDecision.Intent.GENERAL, decision.getIntent());
        assertTrue(decision.getAnswer().contains("George"));
        assertTrue(decision.getNotice().contains("offline mode"));
        assertTrue(decision.getActivityIds().isEmpty());
    }

    @Test
    void misclassifiedGeneralActivityClaimIsRejectedForGroundedFallback() {
        final FallbackTripAssistantGateway gateway = new FallbackTripAssistantGateway(
                ignored -> {
                    return new TripAssistantDecision(
                            TripAssistantDecision.Intent.GENERAL, Collections.emptyList(),
                            "Its signature drink costs $12.", "");
                },
                new OfflineTripAssistantGateway());
        final TripAssistantMessage recommendation = new TripAssistantMessage(
                TripAssistantMessage.Role.ASSISTANT, "Try Real Activity",
                Collections.singletonList("real"));
        final TripAssistantRequest request = request(
                "What is its specialty?", Collections.singletonList(recommendation));

        final TripAssistantDecision decision = gateway.answer(request);

        assertEquals(TripAssistantDecision.Intent.ACTIVITY_DETAILS, decision.getIntent());
        assertEquals(Collections.singletonList("real"), decision.getActivityIds());
        assertEquals(TripAssistantDecision.RequestedFact.SPECIALTY,
                decision.getRequestedFact());
        assertTrue(decision.getAnswer().isEmpty());
        assertTrue(decision.getNotice().contains("offline mode"));
    }

    private TripAssistantRequest request() {
        return request("Recommend an activity");
    }

    private TripAssistantRequest request(String question) {
        return request(question, Collections.emptyList());
    }

    private TripAssistantRequest request(
            String question, java.util.List<TripAssistantMessage> history) {
        final Activity real = new Activity("real", "Real Activity", ActivityCategory.MUSEUM,
                new Location(43.6, -79.3, "Address"), 4.8, 60,
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                IndoorOutdoorType.INDOOR, "Low");
        return new TripAssistantRequest(
                "Toronto", LocalDate.of(2026, 8, 20), LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.WALKING,
                Collections.singletonList(real), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(),
                history, question);
    }
}
