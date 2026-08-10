package use_case.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;
import use_case.search.SearchFailure;
import use_case.search.SearchSource;

final class SearchActivitiesUseCaseTest {

    @Test
    void delegatesTheCompleteRequestAndReturnsTheGatewayResult() {
        ActivitySearchRequest request = new ActivitySearchRequest(
                "Toronto", "museum", ActivityCategory.MUSEUM,
                IndoorOutdoorType.INDOOR, 25);
        ActivitySearchResult expected = new ActivitySearchResult(
                Collections.singletonList(museum()), SearchSource.NOMINATIM,
                false, SearchFailure.NONE);
        AtomicReference<ActivitySearchRequest> received = new AtomicReference<>();
        SearchActivitiesUseCase interactor = new SearchActivitiesUseCase(input -> {
            received.set(input);
            return expected;
        });

        ActivitySearchResult actual = interactor.execute(request);

        assertSame(request, received.get());
        assertSame(expected, actual);
        assertEquals("Royal Ontario Museum", actual.getActivities().get(0).getName());
    }

    @Test
    void requiresAnActivitySearchGateway() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new SearchActivitiesUseCase(null));

        assertEquals("Activity search gateway is required", failure.getMessage());
    }

    @Test
    void rejectsAMissingSearchRequestWithoutCallingTheGateway() {
        SearchActivitiesUseCase interactor = new SearchActivitiesUseCase(input -> {
            throw new AssertionError("Gateway must not be called");
        });

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> interactor.execute(null));

        assertEquals("Activity search request is required", failure.getMessage());
    }

    private static Activity museum() {
        return new Activity("osm-relation-1", "Royal Ontario Museum",
                ActivityCategory.MUSEUM,
                new Location(43.6677, -79.3948, "100 Queens Park, Toronto"),
                0.0, 120, LocalTime.of(10, 0), LocalTime.of(17, 30),
                IndoorOutdoorType.INDOOR, "Low");
    }
}
