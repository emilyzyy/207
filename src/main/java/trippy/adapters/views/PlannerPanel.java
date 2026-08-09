package trippy.adapters.views;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** Focused planner workspace containing the major feature areas. */
public final class PlannerPanel extends JPanel {

    public PlannerPanel(
            SearchPanel searchPanel,
            BookmarksPanel bookmarksPanel,
            DayPlanPanel dayPlanPanel) {
        this(searchPanel, bookmarksPanel, dayPlanPanel, null);
    }

    public PlannerPanel(
            SearchPanel searchPanel,
            BookmarksPanel bookmarksPanel,
            DayPlanPanel dayPlanPanel,
            DaySwitcherPanel daySwitcherPanel) {
        setLayout(new BorderLayout());
        setBackground(SwingTheme.BACKGROUND);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setUI(new SwingTheme.MinimalTabbedPaneUI());
        tabs.setBorder(null);
        tabs.setFont(SwingTheme.BODY);
        tabs.addTab("Search", searchPanel);
        tabs.addTab("Bookmarks", bookmarksPanel);
        tabs.addTab("Day Plan", dayPlanPanel);
        add(tabs, BorderLayout.CENTER);

        if (daySwitcherPanel != null) {
            add(daySwitcherPanel, BorderLayout.NORTH);
        }
    }
}
