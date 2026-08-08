package closeai.infrastructure.ai;

import closeai.application.tripassistant.TripAssistantDecision;
import closeai.application.tripassistant.TripAssistantRequest;
import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FallbackTripAssistantGatewayTest {

    @Test
    void unknownLiveActivityTriggersOfflineGroundedFallback() {
        FallbackTripAssistantGateway gateway = new FallbackTripAssistantGateway(
                ignored -> new TripAssistantDecision(
                        TripAssistantDecision.Intent.RECOMMEND,
                        Collections.singletonList("invented")),
                new OfflineTripAssistantGateway());

        TripAssistantDecision decision = gateway.answer(request());

        assertEquals(Collections.singletonList("real"), decision.getActivityIds());
        assertTrue(decision.getNotice().contains("offline"));
    }

    @Test
    void groundedGeneralAnswerUsesLiveAi() {
        FallbackTripAssistantGateway gateway = new FallbackTripAssistantGateway(
                ignored -> new TripAssistantDecision(
                        TripAssistantDecision.Intent.GENERAL, Collections.emptyList(),
                        "I'm George, and 3 + 3 is 6.", ""),
                new OfflineTripAssistantGateway());

        TripAssistantDecision decision = gateway.answer(request());

        assertEquals("I'm George, and 3 + 3 is 6.", decision.getAnswer());
        assertTrue(decision.getActivityIds().isEmpty());
        assertTrue(decision.getNotice().isEmpty());
    }

    private TripAssistantRequest request() {
        Activity real = new Activity("real", "Real Activity", ActivityCategory.MUSEUM,
                new Location(43.6, -79.3, "Address"), 4.8, 60,
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                IndoorOutdoorType.INDOOR, "Low");
        return new TripAssistantRequest(
                "Toronto", LocalDate.of(2026, 8, 20), LocalTime.of(9, 0),
                LocalTime.of(18, 0), TransportationMode.WALKING,
                Collections.singletonList(real), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), "Recommend an activity");
    }
}
