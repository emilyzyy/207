package trippy.adapters.views;

import trippy.adapters.controllers.ShareTripController;
import trippy.adapters.viewmodels.DashboardState;
import trippy.adapters.viewmodels.DashboardViewModel;
import trippy.adapters.viewmodels.DayPlanViewModel;
import trippy.domain.entities.User;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Persistent application header for identity and active-trip context. */
public final class HeaderPanel extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d");
    private static final int AVATAR_SIZE = 34;

    private final DashboardViewModel viewModel;
    private final DayPlanViewModel dayPlanViewModel;
    private final JLabel tripLabel = new JLabel();
    private final JLabel dateLabel = new JLabel();
    private final JButton shareButton = SwingTheme.primaryButton("Share");
    private final ThemeToggleButton themeToggle = new ThemeToggleButton();
    private final BadgedButton friendsButton = new BadgedButton("Friends");
    private final JButton authButton = new JButton("Sign in");
    private final JButton avatarButton = AvatarSupport.avatarButton(null, AVATAR_SIZE);
    private Runnable openShareAction = () -> { };
    private Runnable onHomeAction = () -> { };
    private Runnable onAuthAction = () -> { };
    private Runnable onProfileAction = () -> { };
    private Runnable onFriendsAction = () -> { };

    public HeaderPanel(
            DashboardViewModel viewModel,
            DayPlanViewModel dayPlanViewModel,
            ShareTripController shareController) {
        if (viewModel == null || dayPlanViewModel == null || shareController == null) {
            throw new IllegalArgumentException("Header dependencies are required");
        }
        this.viewModel = viewModel;
        this.dayPlanViewModel = dayPlanViewModel;
        setLayout(new BorderLayout(24, 0));
        setBackground(SwingTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(13, 22, 13, 22));

        JLabel brand = new JLabel("Trippy");
        brand.setFont(SwingTheme.TITLE);
        brand.setForeground(SwingTheme.NAVY);
        brand.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        brand.setToolTipText("Back to My Trips");
        brand.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                brand.setForeground(SwingTheme.BLUE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                brand.setForeground(SwingTheme.NAVY);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                onHomeAction.run();
            }
        });
        add(brand, BorderLayout.WEST);

        JPanel tripSummary = new JPanel();
        tripSummary.setOpaque(false);
        tripSummary.setLayout(new BoxLayout(tripSummary, BoxLayout.Y_AXIS));
        tripLabel.setFont(SwingTheme.HEADING);
        tripLabel.setForeground(SwingTheme.NAVY);
        // The day being planned is the single most orienting fact on the screen, and it
        // was set in the same small muted type as a caption.
        dateLabel.setFont(SwingTheme.BODY.deriveFont(java.awt.Font.BOLD));
        dateLabel.setForeground(SwingTheme.BLUE);
        tripSummary.add(tripLabel);
        tripSummary.add(Box.createVerticalStrut(3));
        tripSummary.add(dateLabel);
        add(tripSummary, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        shareButton.setName("share-trip");
        shareButton.addActionListener(event -> {
            shareController.execute();
            openShareAction.run();
        });
        actions.add(themeToggle);
        actions.add(shareButton);
        friendsButton.setVisible(false);
        friendsButton.addActionListener(event -> onFriendsAction.run());
        actions.add(friendsButton);
        avatarButton.setVisible(false);
        avatarButton.addActionListener(event -> onProfileAction.run());
        actions.add(avatarButton);
        authButton.setFont(SwingTheme.BODY);
        authButton.setVisible(false);
        authButton.addActionListener(event -> onAuthAction.run());
        actions.add(authButton);
        add(actions, BorderLayout.EAST);

        refresh(viewModel.getState());
        refreshShareAvailability();
        viewModel.addPropertyChangeListener(event -> refresh(viewModel.getState()));
        dayPlanViewModel.addPropertyChangeListener(event -> refreshShareAvailability());
    }

    public void setOpenShareAction(Runnable action) {
        openShareAction = action == null ? () -> { } : action;
    }

    public void setOnHomeAction(Runnable onHomeAction) {
        this.onHomeAction = onHomeAction;
    }

    public void setAuthAction(Runnable action, boolean signedIn) {
        this.onAuthAction = action == null ? () -> { } : action;
        authButton.setVisible(action != null && !signedIn);
        authButton.setText("Sign in");
        friendsButton.setVisible(action != null && signedIn);
        avatarButton.setVisible(action != null && signedIn);
    }

    public void setProfileAction(Runnable action) {
        this.onProfileAction = action == null ? () -> { } : action;
    }

    public void setFriendsAction(Runnable action) {
        this.onFriendsAction = action == null ? () -> { } : action;
    }

    public void setIncomingFriendRequestCount(int count) {
        friendsButton.setBadgeCount(count);
        friendsButton.setToolTipText(count <= 0
                ? "Friends"
                : count + " incoming friend request" + (count == 1 ? "" : "s"));
    }

    public void setProfileUser(User user) {
        avatarButton.setIcon(AvatarSupport.iconFor(user, AVATAR_SIZE));
        avatarButton.setToolTipText(user == null ? "Profile" : "@" + user.getUsername());
        avatarButton.revalidate();
        avatarButton.repaint();
    }

    private void refresh(DashboardState state) {
        tripLabel.setText(state.getDestination().isEmpty()
                ? "Create a trip to begin" : state.getDestination() + " day trip");
        dateLabel.setText(state.getDate() == null
                ? "Date not selected" : DATE.format(state.getDate()));
    }

    private void refreshShareAvailability() {
        shareButton.setEnabled(!dayPlanViewModel.getState().getTripId().isEmpty());
        shareButton.setToolTipText(shareButton.isEnabled()
                ? "Preview and copy this itinerary"
                : "Create a trip before sharing");
    }
}
