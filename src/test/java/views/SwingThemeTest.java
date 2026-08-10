package views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import entity.valueobjects.ActivityCategory;

final class SwingThemeTest {
    @AfterEach void restoreLightTheme() {
        SwingTheme.setDarkMode(false);
    }

    @Test
    void everyCategoryHasADistinctNonBlueSurface() {
        final Set<Color> colors = new HashSet<>();
        for (ActivityCategory category : ActivityCategory.values()) {
            final Color surface = SwingTheme.categorySurface(category);
            colors.add(surface);
            assertNotEquals(SwingTheme.BLUE, surface);
        }
        assertEquals(ActivityCategory.values().length, colors.size());
    }

    @Test
    void darkModeChangesGlobalSurfacesAndCanBeRestored() {
        final Color lightPanel = SwingTheme.PANEL;
        SwingTheme.setDarkMode(true);
        assertTrue(SwingTheme.isDarkMode());
        assertNotEquals(lightPanel, SwingTheme.PANEL);
        SwingTheme.setDarkMode(false);
        assertEquals(lightPanel, SwingTheme.PANEL);
    }

    @Test
    void darkModeUsesThemeControlledButtonAndTabDelegates() {
        SwingTheme.setDarkMode(true);

        final JButton button = SwingTheme.secondaryButton("Edit");
        final JTabbedPane tabs = new JTabbedPane();

        assertTrue(button.getUI() instanceof BasicButtonUI);
        assertTrue(tabs.getUI() instanceof BasicTabbedPaneUI);
        assertEquals(SwingTheme.PANEL, button.getBackground());
        assertEquals(SwingTheme.NAVY, button.getForeground());
    }

    @Test
    void comboBoxFieldAndPopupRowsFollowTheCurrentTheme() {
        SwingTheme.setDarkMode(true);
        final JComboBox<String> combo = new JComboBox<>(new String[] {"09", "10"});
        SwingTheme.styleComboBox(combo);
        final JList<String> popup = new JList<>();

        final java.awt.Component ordinary = combo.getRenderer()
                .getListCellRendererComponent(popup, "09", 0, false, false);
        final Color ordinaryBackground = ordinary.getBackground();
        final java.awt.Component selected = combo.getRenderer()
                .getListCellRendererComponent(popup, "10", 1, true, false);

        assertTrue(combo.getUI() instanceof SwingTheme.ThemedComboBoxUI);
        assertEquals(SwingTheme.PANEL, combo.getBackground());
        assertEquals(SwingTheme.NAVY, combo.getForeground());
        assertEquals(SwingTheme.PANEL, ordinaryBackground);
        assertEquals(SwingTheme.BLUE_SOFT, selected.getBackground());
        assertEquals(SwingTheme.NAVY, selected.getForeground());
    }
}
