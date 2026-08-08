package closeai.adapters.views;

import closeai.adapters.controllers.TripSetupController;
import closeai.adapters.viewmodels.TripAccessViewModel;
import closeai.adapters.viewmodels.TripOptionsState;
import closeai.adapters.viewmodels.TripOptionsViewModel;
import closeai.application.ports.AccountService;
import closeai.domain.entities.TripParticipant;
import closeai.domain.entities.User;
import closeai.domain.valueobjects.TripAccessLevel;
import closeai.domain.valueobjects.TripAccessRole;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Editable Create Trip and Trip Options view, including shared-friend access. */
public final class TripOptionsPanel extends JPanel {
    private final TripOptionsViewModel viewModel;
    private final TripSetupController controller;
    private final AccountService account;
    private final TripAccessViewModel tripAccess;
    private final JTextField destination = new JTextField();
    private final JTextField date = new JTextField();
    private final JTextField startTime = new JTextField();
    private final JTextField endTime = new JTextField();
    private final JLabel status = new JLabel();
    private final JButton submit = SwingTheme.primaryButton("Create Trip");
    private final JPanel sharingSection = new JPanel(new BorderLayout(0, 8));
    private final JPanel accessRows = new JPanel();
    private final JLabel sharingStatus = new JLabel(" ");
    private final JButton saveSharing = SwingTheme.secondaryButton("Save sharing");
    private final Map<String, TripAccessRole> memberRoles = new LinkedHashMap<>();
    private final Map<String, User> memberUsers = new LinkedHashMap<>();
    private List<User> friendChoices = new ArrayList<>();
    private User ownerUser;
    private String currentUserId;
    private boolean canManagePeople;
    private boolean canEditItinerary = true;

    public TripOptionsPanel(TripOptionsViewModel viewModel) {
        this(viewModel, null, null, null);
    }

    public TripOptionsPanel(
            TripOptionsViewModel viewModel, TripSetupController controller) {
        this(viewModel, controller, null, null);
    }

    public TripOptionsPanel(
            TripOptionsViewModel viewModel,
            TripSetupController controller,
            AccountService account) {
        this(viewModel, controller, account, null);
    }

    public TripOptionsPanel(
            TripOptionsViewModel viewModel,
            TripSetupController controller,
            AccountService account,
            TripAccessViewModel tripAccess) {
        if (viewModel == null) {
            throw new IllegalArgumentException("Trip Options ViewModel is required");
        }
        this.viewModel = viewModel;
        this.controller = controller;
        this.account = account;
        this.tripAccess = tripAccess;
        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Trip Options");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        heading.add(title);
        heading.add(Box.createVerticalStrut(4));
        JLabel notice = new JLabel(
                "Edit destination and hours, and manage who can access this trip.");
        notice.setFont(SwingTheme.SMALL);
        notice.setForeground(SwingTheme.MUTED);
        heading.add(notice);
        add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        fields.setAlignmentX(LEFT_ALIGNMENT);
        addField(fields, 0, "Destination", destination);
        addField(fields, 1, "Date (YYYY-MM-DD)", date);
        addField(fields, 2, "Day starts (HH:MM)", startTime);
        addField(fields, 3, "Day ends (HH:MM)", endTime);
        center.add(fields);
        center.add(Box.createVerticalStrut(14));

        buildSharingSection();
        sharingSection.setAlignmentX(LEFT_ALIGNMENT);
        sharingSection.setVisible(false);
        center.add(sharingSection);

        JScrollPane centerScroll = new JScrollPane(center);
        centerScroll.setBorder(BorderFactory.createEmptyBorder());
        centerScroll.getVerticalScrollBar().setUnitIncrement(12);
        add(centerScroll, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        status.setFont(SwingTheme.SMALL);
        footer.add(status);
        footer.add(Box.createVerticalStrut(8));
        submit.setEnabled(controller != null);
        submit.addActionListener(event -> submit());
        footer.add(submit);
        add(footer, BorderLayout.SOUTH);

        renderState(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> {
            if ("feedback".equals(event.getPropertyName())) {
                renderFeedback(viewModel.getState());
            } else {
                renderState(viewModel.getState());
            }
        });
        if (tripAccess != null) {
            tripAccess.addPropertyChangeListener(event -> applyEditability());
        }
    }

    public JButton getSubmitButton() {
        return submit;
    }

    private void buildSharingSection() {
        sharingSection.setOpaque(false);
        JLabel shareTitle = new JLabel("Who has access");
        shareTitle.setFont(SwingTheme.BODY.deriveFont(java.awt.Font.BOLD));
        shareTitle.setForeground(SwingTheme.NAVY);
        sharingSection.add(shareTitle, BorderLayout.NORTH);

        accessRows.setLayout(new BoxLayout(accessRows, BoxLayout.Y_AXIS));
        accessRows.setOpaque(false);
        accessRows.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane scroll = new JScrollPane(accessRows);
        scroll.setBorder(BorderFactory.createLineBorder(SwingTheme.LINE));
        scroll.setPreferredSize(new Dimension(320, 180));
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        sharingSection.add(scroll, BorderLayout.CENTER);

        JPanel shareFooter = new JPanel(new BorderLayout(8, 0));
        shareFooter.setOpaque(false);
        sharingStatus.setFont(SwingTheme.SMALL);
        sharingStatus.setForeground(SwingTheme.MUTED);
        shareFooter.add(sharingStatus, BorderLayout.CENTER);
        saveSharing.addActionListener(event -> saveSharing());
        shareFooter.add(saveSharing, BorderLayout.EAST);
        sharingSection.add(shareFooter, BorderLayout.SOUTH);
    }

    private void submit() {
        if (controller != null && canEditItinerary) {
            controller.execute(
                    destination.getText(),
                    date.getText(),
                    startTime.getText(),
                    endTime.getText());
        }
    }

    private void saveSharing() {
        TripOptionsState state = viewModel.getState();
        if (account == null || !state.hasActiveTrip() || !canManagePeople) {
            return;
        }
        saveSharing.setEnabled(false);
        sharingStatus.setForeground(SwingTheme.MUTED);
        sharingStatus.setText("Saving…");
        Map<String, TripAccessRole> roles = new HashMap<>(memberRoles);
        // Non-owner admins cannot drop themselves when saving.
        if (currentUserId != null && ownerUser != null
                && !currentUserId.equals(ownerUser.getId())
                && memberUsers.containsKey(currentUserId)) {
            roles.putIfAbsent(currentUserId,
                    memberRoles.getOrDefault(currentUserId, TripAccessRole.ADMIN));
        }
        String tripId = state.getTripId();
        new Thread(() -> {
            try {
                account.setTripMembers(tripId, roles);
                SwingUtilities.invokeLater(() -> {
                    sharingStatus.setForeground(SwingTheme.SUCCESS);
                    sharingStatus.setText("Sharing updated.");
                    saveSharing.setEnabled(true);
                });
            } catch (RuntimeException exception) {
                SwingUtilities.invokeLater(() -> {
                    sharingStatus.setForeground(SwingTheme.ERROR);
                    sharingStatus.setText(exception.getMessage() == null
                            ? "Could not update sharing." : exception.getMessage());
                    saveSharing.setEnabled(true);
                });
            }
        }, "Save-Trip-Sharing").start();
    }

    private void renderState(TripOptionsState state) {
        destination.setText(state.getDestination());
        date.setText(state.getDate() == null ? "" : state.getDate().toString());
        startTime.setText(
                state.getStartTime() == null ? "" : state.getStartTime().toString());
        endTime.setText(
                state.getEndTime() == null ? "" : state.getEndTime().toString());
        submit.setText(state.hasActiveTrip() ? "Save Trip Options" : "Create Trip");
        renderFeedback(state);
        refreshSharing(state);
    }

    private void applyEditability() {
        if (tripAccess != null) {
            canEditItinerary = tripAccess.canEditItinerary();
            canManagePeople = tripAccess.canManagePeople();
        }
        // Disabled fields cannot be focused or selected — stronger than setEditable(false).
        destination.setEnabled(canEditItinerary);
        date.setEnabled(canEditItinerary);
        startTime.setEnabled(canEditItinerary);
        endTime.setEnabled(canEditItinerary);
        destination.setEditable(canEditItinerary);
        date.setEditable(canEditItinerary);
        startTime.setEditable(canEditItinerary);
        endTime.setEditable(canEditItinerary);
        submit.setEnabled(controller != null && canEditItinerary);
        saveSharing.setVisible(canManagePeople);
        saveSharing.setEnabled(canManagePeople);
    }

    private void refreshSharing(TripOptionsState state) {
        boolean show = account != null && state.hasActiveTrip();
        sharingSection.setVisible(show);
        if (!show) {
            canEditItinerary = true;
            canManagePeople = false;
            applyEditability();
            return;
        }
        sharingStatus.setForeground(SwingTheme.MUTED);
        sharingStatus.setText("Loading access…");
        String tripId = state.getTripId();
        new Thread(() -> {
            try {
                TripAccessLevel access = account.getMyTripAccess(tripId);
                List<TripParticipant> participants = account.listTripParticipants(tripId);
                List<User> friends = account.listFriends();
                String selfId = account.currentProfile().map(User::getId).orElse(null);
                User owner = null;
                Map<String, TripAccessRole> roles = new LinkedHashMap<>();
                Map<String, User> users = new LinkedHashMap<>();
                for (TripParticipant participant : participants) {
                    if (participant.isOwner()) {
                        owner = participant.getUser();
                    } else {
                        roles.put(participant.getUser().getId(), participant.getRole());
                        users.put(participant.getUser().getId(), participant.getUser());
                    }
                }
                User resolvedOwner = owner;
                SwingUtilities.invokeLater(() -> {
                    if (!tripId.equals(viewModel.getState().getTripId())) {
                        return;
                    }
                    canEditItinerary = access.canEditItinerary();
                    canManagePeople = access.canManagePeople();
                    if (tripAccess != null) {
                        tripAccess.setAccess(canEditItinerary, canManagePeople);
                    }
                    ownerUser = resolvedOwner;
                    currentUserId = selfId;
                    friendChoices = friends;
                    memberRoles.clear();
                    memberRoles.putAll(roles);
                    memberUsers.clear();
                    memberUsers.putAll(users);
                    applyEditability();
                    rebuildAccessRows();
                    if (!canEditItinerary) {
                        sharingStatus.setText("View only — you can see this trip but not edit it.");
                    } else if (!canManagePeople) {
                        sharingStatus.setText(
                                "You can edit the itinerary, but only admins manage access.");
                    } else if (friends.isEmpty() && memberRoles.isEmpty()) {
                        sharingStatus.setText("Add friends from the Friends button to share trips.");
                    } else {
                        sharingStatus.setText(
                                "Owner cannot be removed. Set View, Edit, or Admin for each person.");
                    }
                });
            } catch (RuntimeException exception) {
                SwingUtilities.invokeLater(() -> {
                    sharingStatus.setForeground(SwingTheme.ERROR);
                    sharingStatus.setText(exception.getMessage() == null
                            ? "Could not load sharing." : exception.getMessage());
                });
            }
        }, "Load-Trip-Sharing").start();
    }

    private void rebuildAccessRows() {
        accessRows.removeAll();
        if (ownerUser != null) {
            accessRows.add(new OwnerRow(ownerUser));
            accessRows.add(Box.createVerticalStrut(6));
        }

        if (canManagePeople) {
            Map<String, User> shown = new LinkedHashMap<>();
            for (User friend : friendChoices) {
                if (ownerUser != null && friend.getId().equals(ownerUser.getId())) {
                    continue;
                }
                shown.put(friend.getId(), friend);
            }
            for (Map.Entry<String, User> entry : memberUsers.entrySet()) {
                shown.putIfAbsent(entry.getKey(), entry.getValue());
            }
            if (shown.isEmpty()) {
                JLabel empty = new JLabel("No friends yet.");
                empty.setFont(SwingTheme.SMALL);
                empty.setForeground(SwingTheme.MUTED);
                empty.setAlignmentX(LEFT_ALIGNMENT);
                accessRows.add(empty);
            } else {
                for (User user : shown.values()) {
                    boolean selfLocked = isSelfLockedMember(user.getId());
                    accessRows.add(new MemberAccessRow(user, !selfLocked, selfLocked));
                    accessRows.add(Box.createVerticalStrut(4));
                }
            }
        } else {
            if (memberUsers.isEmpty()) {
                JLabel empty = new JLabel("Only the owner has access so far.");
                empty.setFont(SwingTheme.SMALL);
                empty.setForeground(SwingTheme.MUTED);
                empty.setAlignmentX(LEFT_ALIGNMENT);
                accessRows.add(empty);
            } else {
                for (User user : memberUsers.values()) {
                    accessRows.add(new MemberAccessRow(user, false, false));
                    accessRows.add(Box.createVerticalStrut(4));
                }
            }
        }

        accessRows.revalidate();
        accessRows.repaint();
    }

    /** Admins (non-owners) cannot remove themselves or change their own role. */
    private boolean isSelfLockedMember(String userId) {
        return canManagePeople
                && currentUserId != null
                && currentUserId.equals(userId)
                && (ownerUser == null || !currentUserId.equals(ownerUser.getId()));
    }

    private void renderFeedback(TripOptionsState state) {
        status.setText(state.getMessage().isEmpty()
                ? "Enter trip details to begin." : state.getMessage());
        status.setForeground(state.isError() ? SwingTheme.ERROR : SwingTheme.SUCCESS);
    }

    private void addField(
            JPanel fields, int row, String label, java.awt.Component component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(6, 0, 6, 12);
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(SwingTheme.BODY);
        fieldLabel.setForeground(SwingTheme.NAVY);
        fields.add(fieldLabel, labelConstraints);

        GridBagConstraints valueConstraints = new GridBagConstraints();
        valueConstraints.gridx = 1;
        valueConstraints.gridy = row;
        valueConstraints.weightx = 1;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        valueConstraints.insets = new Insets(6, 0, 6, 0);
        fields.add(component, valueConstraints);
    }

    private final class OwnerRow extends JPanel {
        OwnerRow(User owner) {
            setOpaque(true);
            setBackground(SwingTheme.BACKGROUND);
            setLayout(new FlowLayout(FlowLayout.LEFT, 10, 6));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            add(new JLabel(AvatarSupport.iconFor(owner, 26)));
            JLabel name = new JLabel("@" + owner.getUsername() + " (Owner)");
            name.setFont(SwingTheme.BODY.deriveFont(java.awt.Font.BOLD));
            name.setForeground(SwingTheme.NAVY);
            add(name);
        }
    }

    private final class MemberAccessRow extends JPanel {
        private final User friend;
        private boolean selected;
        private final JComboBox<String> roleBox;

        MemberAccessRow(User friend, boolean editable, boolean selfLocked) {
            this.friend = friend;
            this.selected = memberRoles.containsKey(friend.getId());
            TripAccessRole role = memberRoles.getOrDefault(friend.getId(), TripAccessRole.EDIT);
            setOpaque(true);
            setBackground(selected || !editable || selfLocked
                    ? SwingTheme.BLUE_SOFT : SwingTheme.BACKGROUND);
            setLayout(new BorderLayout(8, 0));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
            left.setOpaque(false);
            if (editable) {
                left.add(new CircularCheck());
                left.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                left.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        toggle();
                    }
                });
            }
            left.add(new JLabel(AvatarSupport.iconFor(friend, 26)));
            JLabel name = new JLabel("@" + friend.getUsername()
                    + (selfLocked ? " (You)" : ""));
            name.setFont(SwingTheme.BODY);
            name.setForeground(SwingTheme.NAVY);
            left.add(name);
            add(left, BorderLayout.CENTER);

            roleBox = new JComboBox<>(new String[] {
                    TripAccessRole.VIEW.displayName(),
                    TripAccessRole.EDIT.displayName(),
                    TripAccessRole.ADMIN.displayName()
            });
            roleBox.setSelectedItem(role.displayName());
            roleBox.setFont(SwingTheme.SMALL);
            if (selfLocked) {
                // No checkbox / role dropdown — admins cannot change their own access.
                roleBox.setVisible(false);
                JLabel roleLabel = new JLabel(role.displayName());
                roleLabel.setFont(SwingTheme.SMALL);
                roleLabel.setForeground(SwingTheme.MUTED);
                add(roleLabel, BorderLayout.EAST);
            } else if (editable) {
                roleBox.setVisible(selected);
                roleBox.setEnabled(selected);
                roleBox.addActionListener(event -> {
                    if (!selected) {
                        return;
                    }
                    memberRoles.put(friend.getId(),
                            roleFromDisplay((String) roleBox.getSelectedItem()));
                });
                add(roleBox, BorderLayout.EAST);
            } else {
                roleBox.setEnabled(false);
                roleBox.setVisible(true);
                add(roleBox, BorderLayout.EAST);
            }
        }

        void toggle() {
            selected = !selected;
            if (selected) {
                TripAccessRole role = roleFromDisplay((String) roleBox.getSelectedItem());
                memberRoles.put(friend.getId(), role);
                memberUsers.put(friend.getId(), friend);
                setBackground(SwingTheme.BLUE_SOFT);
                roleBox.setVisible(true);
                roleBox.setEnabled(true);
            } else {
                memberRoles.remove(friend.getId());
                setBackground(SwingTheme.BACKGROUND);
                roleBox.setEnabled(false);
                roleBox.setVisible(false);
            }
            repaint();
        }

        private TripAccessRole roleFromDisplay(String display) {
            if (TripAccessRole.VIEW.displayName().equals(display)) {
                return TripAccessRole.VIEW;
            }
            if (TripAccessRole.ADMIN.displayName().equals(display)) {
                return TripAccessRole.ADMIN;
            }
            return TripAccessRole.EDIT;
        }

        private final class CircularCheck extends JPanel {
            CircularCheck() {
                setOpaque(false);
                setPreferredSize(new Dimension(18, 18));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = Math.min(getWidth(), getHeight()) - 2;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                if (selected) {
                    g2.setColor(SwingTheme.BLUE);
                    g2.fill(new Ellipse2D.Float(x, y, size, size));
                    g2.setColor(Color.WHITE);
                    g2.drawLine(x + 4, y + size / 2, x + size / 2 - 1, y + size - 5);
                    g2.drawLine(x + size / 2 - 1, y + size - 5, x + size - 4, y + 4);
                } else {
                    g2.setColor(SwingTheme.PANEL);
                    g2.fill(new Ellipse2D.Float(x, y, size, size));
                    g2.setColor(SwingTheme.LINE);
                    g2.draw(new Ellipse2D.Float(x, y, size - 1, size - 1));
                }
                g2.dispose();
            }
        }
    }
}
