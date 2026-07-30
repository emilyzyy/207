package closeai.adapters.views;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** Focused planner workspace containing the four major feature areas. */
public final class PlannerPanel extends JPanel {

    public PlannerPanel(
            SearchPanel searchPanel,
            BookmarksPanel bookmarksPanel,
            DayPlanPanel dayPlanPanel,
            TripOptionsPanel tripOptionsPanel) {
        setLayout(new BorderLayout());
        setBackground(SwingTheme.PANEL);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(SwingTheme.BODY);
        tabs.addTab("Search", searchPanel);
        tabs.addTab("Bookmarks", bookmarksPanel);
        tabs.addTab("Day Plan", dayPlanPanel);
        tabs.addTab("Trip Options", tripOptionsPanel);
        add(tabs, BorderLayout.CENTER);
    }
}
