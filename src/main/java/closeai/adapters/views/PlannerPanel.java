package closeai.adapters.views;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** Focused planner workspace containing the major feature areas. */
public final class PlannerPanel extends JPanel {

    public PlannerPanel(
            SearchPanel searchPanel,
            BookmarksPanel bookmarksPanel,
            DayPlanPanel dayPlanPanel,
            TripOptionsPanel tripOptionsPanel) {
        this(searchPanel, bookmarksPanel, dayPlanPanel, null, tripOptionsPanel, null);
    }

    public PlannerPanel(
            SearchPanel searchPanel,
            BookmarksPanel bookmarksPanel,
            DayPlanPanel dayPlanPanel,
            TripOptionsPanel tripOptionsPanel,
            DaySwitcherPanel daySwitcherPanel) {
        this(searchPanel, bookmarksPanel, dayPlanPanel, null, tripOptionsPanel, daySwitcherPanel);
    }

    public PlannerPanel(
            SearchPanel searchPanel,
            BookmarksPanel bookmarksPanel,
            DayPlanPanel dayPlanPanel,
            TripAssistantPanel tripAssistantPanel,
            TripOptionsPanel tripOptionsPanel) {
        this(searchPanel, bookmarksPanel, dayPlanPanel, tripAssistantPanel, tripOptionsPanel, null);
    }

    /** The day switcher sits above the tabs so it stays visible from any tab. */
    public PlannerPanel(
            SearchPanel searchPanel,
            BookmarksPanel bookmarksPanel,
            DayPlanPanel dayPlanPanel,
            TripAssistantPanel tripAssistantPanel,
            TripOptionsPanel tripOptionsPanel,
            DaySwitcherPanel daySwitcherPanel) {
        setLayout(new BorderLayout());
        setBackground(SwingTheme.PANEL);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(SwingTheme.BODY);
        tabs.addTab("Search", searchPanel);
        tabs.addTab("Bookmarks", bookmarksPanel);
        tabs.addTab("Day Plan", dayPlanPanel);
        if (tripAssistantPanel != null) {
            tabs.addTab("Trip Assistant", tripAssistantPanel);
        }
        tabs.addTab("Trip Options", tripOptionsPanel);
        add(tabs, BorderLayout.CENTER);

        if (daySwitcherPanel != null) {
            add(daySwitcherPanel, BorderLayout.NORTH);
        }
    }
}
