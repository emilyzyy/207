package views;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import entity.valueobjects.ActivityCategory;
import org.junit.jupiter.api.Test;

class ActivityCategoryPresentationTest {
    @Test
    void everyCategoryHasAnIconAndKeepsItsTextLabel() {
        for (ActivityCategory category : ActivityCategory.values()) {
            assertFalse(ActivityCategoryPresentation.icon(category).isEmpty(),
                    category + " should have a non-colour icon");
            assertTrue(ActivityCategoryPresentation.decorate(category, "Place")
                    .endsWith("Place"));
        }
    }
}
