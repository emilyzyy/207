package closeai.adapters.views;

import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.adapters.viewmodels.CalendarViewModel;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.SearchViewModel;
import closeai.adapters.viewmodels.ShareViewModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

/** Main Swing frame for the milestone dashboard. */
public final class CloseAIFrame extends JFrame {
    private final CalendarDialog calendarDialog;
    private final ShareDialog shareDialog;
    private final DayPlanPanel dayPlanPanel;
    private final HeaderPanel headerPanel;
    private final TripAssistantPanel tripAssistantPanel;
    private final DayPlanViewModel dayPlanViewModel;
    private final SearchViewModel searchViewModel;
    private final BookmarksViewModel bookmarksViewModel;

    public CloseAIFrame(
            HeaderPanel headerPanel,
            OverviewPanel overviewPanel,
            PlannerPanel plannerPanel,
            DayPlanPanel dayPlanPanel,
            TripAssistantPanel tripAssistantPanel,
            DayPlanViewModel dayPlanViewModel,
            CalendarViewModel calendarViewModel,
            ShareViewModel shareViewModel,
            SearchViewModel searchViewModel,
            BookmarksViewModel bookmarksViewModel) {
        super("CloseAI Trip Planner");
        this.headerPanel = headerPanel;
        this.tripAssistantPanel = tripAssistantPanel;
        this.dayPlanViewModel = dayPlanViewModel;
        this.searchViewModel = searchViewModel;
        this.bookmarksViewModel = bookmarksViewModel;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 680));
        setPreferredSize(new Dimension(1320, 820));

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(SwingTheme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));
        root.add(headerPanel, BorderLayout.NORTH);

        JSplitPane content = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, overviewPanel, plannerPanel);
        content.setResizeWeight(0.42);
        content.setDividerSize(8);
        content.setBorder(BorderFactory.createEmptyBorder());
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);

        this.dayPlanPanel = dayPlanPanel;
        calendarDialog = new CalendarDialog(this, calendarViewModel, dayPlanViewModel);
        dayPlanPanel.setOpenCalendarAction(() -> {
            calendarDialog.setLocationRelativeTo(this);
            calendarDialog.setVisible(true);
        });
        shareDialog = new ShareDialog(this, shareViewModel);
        headerPanel.setOpenShareAction(() -> {
            shareDialog.setLocationRelativeTo(this);
            shareDialog.setVisible(true);
        });

        pack();
        setLocationRelativeTo(null);
    }

    public CalendarDialog getCalendarDialog() {
        return calendarDialog;
    }

    /** Wires the brand click in the header to return to the gallery. */
    public void setOnHomeAction(Runnable onHomeAction) {
        headerPanel.setOnHomeAction(onHomeAction);
    }

    public void setAuthAction(Runnable action, boolean signedIn) {
        headerPanel.setAuthAction(action, signedIn);
    }

    public void setProfileAction(Runnable action) {
        headerPanel.setProfileAction(action);
    }

    public void setFriendsAction(Runnable action) {
        headerPanel.setFriendsAction(action);
    }

    public void setProfileUser(closeai.domain.entities.User user) {
        headerPanel.setProfileUser(user);
    }

    public DayPlanPanel getDayPlanPanel() {
        return dayPlanPanel;
    }

    public TripAssistantPanel getTripAssistantPanel() {
        return tripAssistantPanel;
    }

    public ShareDialog getShareDialog() {
        return shareDialog;
    }

    public DayPlanViewModel getDayPlanViewModel() {
        return dayPlanViewModel;
    }

    public SearchViewModel getSearchViewModel() {
        return searchViewModel;
    }

    public BookmarksViewModel getBookmarksViewModel() {
        return bookmarksViewModel;
    }
}
