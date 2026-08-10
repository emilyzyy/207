package views;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import entity.entities.User;
import interface_adapter.viewmodels.BookmarksViewModel;
import interface_adapter.viewmodels.CalendarViewModel;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.SearchViewModel;
import interface_adapter.viewmodels.ShareViewModel;

/** Main Swing frame for the milestone dashboard. */
public final class TrippyFrame extends JFrame {
    private final CalendarDialog calendarDialog;
    private final ShareDialog shareDialog;
    private final DayPlanPanel dayPlanPanel;
    private final HeaderPanel headerPanel;
    private final TripAssistantPanel tripAssistantPanel;
    private final FloatingTripAssistantWidget tripAssistantWidget;
    private final DayPlanViewModel dayPlanViewModel;
    private final SearchViewModel searchViewModel;
    private final BookmarksViewModel bookmarksViewModel;

    public TrippyFrame(
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
        super("Trippy Trip Planner");
        this.headerPanel = headerPanel;
        this.tripAssistantPanel = tripAssistantPanel;
        this.dayPlanViewModel = dayPlanViewModel;
        this.searchViewModel = searchViewModel;
        this.bookmarksViewModel = bookmarksViewModel;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 680));
        setPreferredSize(new Dimension(1320, 820));

        final JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(SwingTheme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));
        root.add(headerPanel, BorderLayout.NORTH);

        final JSplitPane content = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, overviewPanel, plannerPanel);
        content.setResizeWeight(0.42);
        content.setDividerSize(8);
        content.setBorder(BorderFactory.createEmptyBorder());
        root.add(content, BorderLayout.CENTER);
        tripAssistantWidget = new FloatingTripAssistantWidget(root, tripAssistantPanel);
        setContentPane(tripAssistantWidget);

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
    /**
     * Wires the brand click in the header to return to the gallery.
     * @param onHomeAction the o nh om ea ct io n value
     */

    public void setOnHomeAction(Runnable onHomeAction) {
        headerPanel.setOnHomeAction(onHomeAction);
    }

    /**
     * Performs the s et au th ac ti on operation.
     * @param signedIn the s ig ne di n value
     * @param action the a ct io n value
     */
    public void setAuthAction(Runnable action, boolean signedIn) {
        headerPanel.setAuthAction(action, signedIn);
    }

    /**
     * Performs the s et pr of il ea ct io n operation.
     * @param action the a ct io n value
     */
    public void setProfileAction(Runnable action) {
        headerPanel.setProfileAction(action);
    }

    /**
     * Performs the s et fr ie nd sa ct io n operation.
     * @param action the a ct io n value
     */
    public void setFriendsAction(Runnable action) {
        headerPanel.setFriendsAction(action);
    }

    /**
     * Performs the s et in co mi ng fr ie nd re qu es tc ou nt operation.
     * @param count the c ou nt value
     */
    public void setIncomingFriendRequestCount(int count) {
        headerPanel.setIncomingFriendRequestCount(count);
    }

    /**
     * Performs the s et pr of il eu se r operation.
     * @param user the u se r value
     */
    public void setProfileUser(User user) {
        headerPanel.setProfileUser(user);
    }

    public DayPlanPanel getDayPlanPanel() {
        return dayPlanPanel;
    }

    public TripAssistantPanel getTripAssistantPanel() {
        return tripAssistantPanel;
    }

    public FloatingTripAssistantWidget getTripAssistantWidget() {
        return tripAssistantWidget;
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
