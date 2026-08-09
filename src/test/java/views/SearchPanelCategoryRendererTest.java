package views;

import entity.valueobjects.ActivityCategory;
import java.awt.Component;
import javax.swing.JList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SearchPanelCategoryRendererTest {
    @AfterEach
    void restoreLightTheme() {
        SwingTheme.setDarkMode(false);
    }

    @Test
    void categoryOptionsUseTheirActivityCardColours() {
        SearchPanel.CategoryFilterRenderer renderer =
                new SearchPanel.CategoryFilterRenderer();
        JList<String> list = new JList<>();

        assertSurface(renderer, list, "All categories", SwingTheme.PANEL);
        assertSurface(renderer, list, "Food",
                SwingTheme.categorySurface(ActivityCategory.FOOD));
        assertSurface(renderer, list, "Museum",
                SwingTheme.categorySurface(ActivityCategory.MUSEUM));
        assertSurface(renderer, list, "Parks/Nature",
                SwingTheme.categorySurface(ActivityCategory.PARKS_NATURE));
        assertSurface(renderer, list, "Sports/Recreation",
                SwingTheme.categorySurface(ActivityCategory.SPORTS_RECREATION));
        assertSurface(renderer, list, "Arts/Culture",
                SwingTheme.categorySurface(ActivityCategory.ARTS_CULTURE));
    }

    @Test
    void categoryOptionsFollowDarkModeCardColours() {
        SwingTheme.setDarkMode(true);
        SearchPanel.CategoryFilterRenderer renderer =
                new SearchPanel.CategoryFilterRenderer();
        JList<String> list = new JList<>();

        assertSurface(renderer, list, "Historic",
                SwingTheme.categorySurface(ActivityCategory.HISTORIC));
        assertSurface(renderer, list, "All categories", SwingTheme.PANEL);
    }

    private void assertSurface(SearchPanel.CategoryFilterRenderer renderer,
                               JList<String> list, String value,
                               java.awt.Color expected) {
        Component component = renderer.getListCellRendererComponent(
                list, value, 0, false, false);
        assertEquals(expected, component.getBackground());
    }
}
