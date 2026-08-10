package use_case.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;

final class ActivitySearchRequestTest {

    @Test
    void trimsUserEnteredDestinationAndQuery() {
        final ActivitySearchRequest request = new ActivitySearchRequest(
                "  Toronto, Ontario, Canada  ", "  Trinity Bellwoods Park  ",
                null, null, 25);

        assertEquals("Toronto, Ontario, Canada", request.getDestination());
        assertEquals("Trinity Bellwoods Park", request.getQuery());
    }

    @Test
    void normalizesMissingTextAndNonPositiveLimits() {
        final ActivitySearchRequest request = new ActivitySearchRequest(
                null, null, null, null, 0);

        assertEquals("", request.getDestination());
        assertEquals("", request.getQuery());
        assertEquals(1, request.getLimit());
    }

    @Test
    void preservesValidLimitAndOptionalDiscoveryFilters() {
        final ActivitySearchRequest request = new ActivitySearchRequest(
                "Milan", "museum", ActivityCategory.MUSEUM,
                IndoorOutdoorType.INDOOR, 75);

        assertEquals(ActivityCategory.MUSEUM, request.getCategory());
        assertEquals(IndoorOutdoorType.INDOOR, request.getSetting());
        assertEquals(75, request.getLimit());
    }

    @Test
    void leavesUnselectedFiltersAbsent() {
        final ActivitySearchRequest request = new ActivitySearchRequest(
                "New York", "park", null, null, 20);

        assertNull(request.getCategory());
        assertNull(request.getSetting());
    }
}
