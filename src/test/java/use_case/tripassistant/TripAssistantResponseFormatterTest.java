package use_case.tripassistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import entity.valueobjects.TripAssistantMessage;

final class TripAssistantResponseFormatterTest {
    private final TripAssistantResponseFormatter formatter =
            new TripAssistantResponseFormatter();
    private final Activity cafe = new Activity(
            "cafe", "Kó-Café", ActivityCategory.COFFEE,
            new Location(43.65, -79.38, "123 Grounded Street"), 4.7, 45,
            LocalTime.of(8, 30), LocalTime.of(17, 0),
            IndoorOutdoorType.INDOOR, "Low");
    private final Activity park = new Activity(
            "park", "Actual Park", ActivityCategory.PARKS_NATURE,
            new Location(43.66, -79.37, "9 Park Lane"), 4.2, 90,
            LocalTime.of(9, 0), LocalTime.of(20, 0),
            IndoorOutdoorType.OUTDOOR, "Medium");

    @Test
    void missingSpecialtyIsHonestAndDoesNotRepeatRecommendationTemplate() {
        final TripAssistantOutputData output = format(
                "What is their specialty?", history("cafe"), "cafe",
                TripAssistantDecision.RequestedFact.SPECIALTY,
                "Its signature drink is a maple latte invented in 1920.");

        assertTrue(output.getAnswer().contains("records Kó-Café as a café"));
        assertTrue(output.getAnswer().contains("doesn't include its menu or signature specialty"));
        assertTrue(output.getAnswer().contains("don't want to make one up"));
        assertFalse(output.getAnswer().contains("maple latte"));
        assertFalse(output.getAnswer().contains("I'd recommend"));
        assertEquals(Collections.singletonList("cafe"), output.getActivityIds());
    }

    @Test
    void factualFollowUpsRenderOnlyRealEntityFields() {
        assertFact(TripAssistantDecision.RequestedFact.RATING,
                "Kó-Café has a 4.7 rating in Trippy.");
        assertFact(TripAssistantDecision.RequestedFact.HOURS,
                "Kó-Café is recorded as open from 8:30 AM to 5:00 PM.");
        assertFact(TripAssistantDecision.RequestedFact.DURATION,
                "Trippy estimates 45 minutes for Kó-Café.");
        assertFact(TripAssistantDecision.RequestedFact.CATEGORY,
                "Trippy records Kó-Café as a café.");
        assertFact(TripAssistantDecision.RequestedFact.LOCATION,
                "Trippy lists Kó-Café at 123 Grounded Street.");
        assertFact(TripAssistantDecision.RequestedFact.SETTING,
                "Kó-Café is recorded as indoors.");
    }

    @Test
    void bookmarkAndDayPlanStatusComeFromCurrentTripContext() {
        final ScheduledEvent event = new ScheduledEvent(
                "event-cafe", cafe, LocalTime.of(10, 0), LocalTime.of(10, 45),
                EventType.ACTIVITY, "Coffee");
        final TripAssistantRequest request = request(
                "Is it in my Day Plan?", history("cafe"),
                Collections.singleton("cafe"), Collections.singletonList(event));

        final TripAssistantOutputData plan = formatter.format(request, details(
                "park", TripAssistantDecision.RequestedFact.PLAN_STATUS, "invented"));
        final TripAssistantOutputData bookmark = formatter.format(request, details(
                "park", TripAssistantDecision.RequestedFact.BOOKMARK_STATUS, "invented"));

        assertEquals("Kó-Café is already in your Day Plan.", plan.getAnswer());
        assertEquals("Kó-Café is in your bookmarks.", bookmark.getAnswer());
    }

    @Test
    void followUpPronounForcesTheMostRecentSingleGroundedActivity() {
        final TripAssistantOutputData output = format(
                "What is its rating?", history("cafe"), "park",
                TripAssistantDecision.RequestedFact.RATING,
                "Actual Park has a perfect rating.");

        assertEquals("Kó-Café has a 4.7 rating in Trippy.", output.getAnswer());
        assertEquals(Collections.singletonList("cafe"), output.getActivityIds());
    }

    @Test
    void ambiguousPronounAsksForClarificationInsteadOfGuessing() {
        final TripAssistantOutputData output = format(
                "When do they open?", history("cafe", "park"), "cafe",
                TripAssistantDecision.RequestedFact.HOURS, "Guess the café");

        assertTrue(output.getAnswer().contains("Which activity do you mean"));
        assertTrue(output.getAnswer().contains("Kó-Café"));
        assertTrue(output.getAnswer().contains("Actual Park"));
        assertFalse(output.getAnswer().contains("08:30"));
    }

    @Test
    void unsupportedFactAndModelTextCannotAddActivityClaims() {
        final TripAssistantOutputData output = format(
                "How expensive is Kó-Café?", Collections.emptyList(), "cafe",
                TripAssistantDecision.RequestedFact.UNKNOWN,
                "Kó-Café costs exactly $12 and serves a secret drink.");

        assertTrue(output.getAnswer().contains("doesn't include that detail"));
        assertTrue(output.getAnswer().contains("don't want to make it up"));
        assertFalse(output.getAnswer().contains("$12"));
        assertFalse(output.getAnswer().contains("secret drink"));
    }

    @Test
    void whyFollowUpGetsAConciseGroundedExplanation() {
        final TripAssistantRequest request = request(
                "Why did you recommend it?", history("cafe"),
                Collections.emptySet(), Collections.emptyList());
        final TripAssistantDecision decision = new TripAssistantDecision(
                TripAssistantDecision.Intent.EXPLAIN,
                Collections.singletonList("cafe"), "It is famous worldwide.", "",
                TripAssistantDecision.RequestedFact.RECOMMENDATION_REASON);

        final TripAssistantOutputData output = formatter.format(request, decision);

        assertTrue(output.getAnswer().startsWith("I recommended Kó-Café because"));
        assertTrue(output.getAnswer().contains("4.7 rating"));
        assertTrue(output.getAnswer().contains("45 minutes"));
        assertFalse(output.getAnswer().contains("famous worldwide"));
        assertFalse(output.getAnswer().contains("I'd recommend"));
    }

    private void assertFact(
            TripAssistantDecision.RequestedFact fact, String expected) {
        final TripAssistantOutputData output = format(
                "Tell me the fact", Collections.emptyList(), "cafe", fact, "invented");
        assertEquals(expected, output.getAnswer());
        assertFalse(output.getAnswer().contains("invented"));
    }

    private TripAssistantOutputData format(
            String question, List<TripAssistantMessage> history, String activityId,
            TripAssistantDecision.RequestedFact fact, String modelAnswer) {
        return formatter.format(
                request(question, history, Collections.emptySet(), Collections.emptyList()),
                details(activityId, fact, modelAnswer));
    }

    private TripAssistantDecision details(
            String activityId, TripAssistantDecision.RequestedFact fact,
            String modelAnswer) {
        return new TripAssistantDecision(
                TripAssistantDecision.Intent.ACTIVITY_DETAILS,
                Collections.singletonList(activityId), modelAnswer, "", fact);
    }

    private TripAssistantRequest request(
            String question, List<TripAssistantMessage> history,
            Set<String> bookmarks, List<ScheduledEvent> events) {
        return new TripAssistantRequest(
                "Toronto", LocalDate.of(2026, 8, 20), LocalTime.of(8, 0),
                LocalTime.of(20, 0), TransportationMode.WALKING,
                Arrays.asList(cafe, park), new HashSet<String>(bookmarks), events,
                Collections.emptyList(), history, question);
    }

    private List<TripAssistantMessage> history(String... activityIds) {
        return Collections.singletonList(new TripAssistantMessage(
                TripAssistantMessage.Role.ASSISTANT, "Earlier recommendation",
                Arrays.asList(activityIds)));
    }
}
