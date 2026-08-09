package trippy.adapters.views;

import trippy.adapters.controllers.TripOptionsController;
import trippy.adapters.viewmodels.TripAccessViewModel;
import trippy.adapters.viewmodels.TripOptionsState;
import trippy.adapters.viewmodels.TripOptionsViewModel;
import trippy.application.ports.AccountService;
import trippy.domain.entities.TripParticipant;
import trippy.domain.entities.User;
import trippy.domain.valueobjects.TripAccessLevel;
import trippy.domain.valueobjects.TripAccessRole;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** Focused modal editor for the active Day Plan's date, time boundaries, and sharing. */
public final class TripOptionsDialog {
    private final java.awt.Component owner;
    private final TripOptionsViewModel viewModel;
    private final TripOptionsController controller;
    private final AccountService account;
    private final TripAccessViewModel tripAccess;
    private final DateSelectionButton date;
    private final TimeSelectorPanel start;
    private final TimeSelectorPanel end;
    private final JLabel feedback = new JLabel();
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
    private JDialog dialog;
    private JButton save;

    public TripOptionsDialog(java.awt.Component owner,
                             TripOptionsViewModel viewModel,
                             TripOptionsController controller) {
        this(owner, viewModel, controller, null, null);
    }

    public TripOptionsDialog(java.awt.Component owner,
                             TripOptionsViewModel viewModel,
                             TripOptionsController controller,
                             AccountService account,
                             TripAccessViewModel tripAccess) {
        if (viewModel == null || controller == null) {
            throw new IllegalArgumentException("Trip Options dialog dependencies are required");
        }
        this.owner = owner;
        this.viewModel = viewModel;
        this.controller = controller;
        this.account = account;
        this.tripAccess = tripAccess;
        TripOptionsState state = viewModel.getState();
        date = new DateSelectionButton(state.getDate());
        start = new TimeSelectorPanel(state.getStartTime());
        end = new TimeSelectorPanel(state.getEndTime());
        if (account != null) {
            buildSharingSection();
        }
    }

    public void showDialog() {
        TripOptionsState state = viewModel.getState();
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        addField(fields, 0, "Destination", boldLabel(state.getDestination()));
        addField(fields, 1, "Trip start date", date);
        addField(fields, 2, "Day starts", start);
        addField(fields, 3, "Day ends", end);
        feedback.setFont(SwingTheme.SMALL);
        GridBagConstraints feedbackConstraints = constraints(1, 4);
        feedbackConstraints.fill = GridBagConstraints.HORIZONTAL;
        fields.add(feedback, feedbackConstraints);

        JButton cancel = SwingTheme.secondaryButton("Cancel");
        save = SwingTheme.primaryButton("Save Options");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        actions.add(cancel);
        actions.add(save);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(fields);
        if (account != null && state.hasActiveTrip()) {
            sharingSection.setVisible(true);
            center.add(Box.createVerticalStrut(12));
            center.add(sharingSection);
            refreshSharing(state);
        } else if (account != null) {
            sharingSection.setVisible(false);
        }

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(center, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        dialog = new JDialog(SwingUtilities.getWindowAncestor(owner),
                "Day Plan Options", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(content);
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            if (canEditItinerary) {
                controller.execute(date.getDate(), start.getTime(), end.getTime());
                TripOptionsState updated = viewModel.getState();
                renderFeedback(updated);
                if (!updated.isError()) {
                    dialog.dispose();
                }
            }
        });
        applyEditability();
        dialog.getRootPane().setDefaultButton(save);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
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

    private void saveSharing() {
        TripOptionsState state = viewModel.getState();
        if (account == null || !state.hasActiveTrip() || !canManagePeople) {
            return;
        }
        saveSharing.setEnabled(false);
        sharingStatus.setForeground(SwingTheme.MUTED);
        sharingStatus.setText("Saving…");
        Map<String, TripAccessRole> roles = new HashMap<>(memberRoles);
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
                    saveSharing.setEnabled(canManagePeople);
                });
            } catch (RuntimeException exception) {
                SwingUtilities.invokeLater(() -> {
                    sharingStatus.setForeground(SwingTheme.ERROR);
                    sharingStatus.setText(exception.getMessage() == null
                            ? "Could not update sharing." : exception.getMessage());
                    saveSharing.setEnabled(canManagePeople);
                });
            }
        }, "Save-Trip-Sharing").start();
    }

    private void applyEditability() {
        if (tripAccess != null) {
            canEditItinerary = tripAccess.canEditItinerary();
            canManagePeople = tripAccess.canManagePeople();
        }
        date.setEnabled(canEditItinerary);
        setTimeSelectorEnabled(start, canEditItinerary);
        setTimeSelectorEnabled(end, canEditItinerary);
        if (save != null) {
            save.setEnabled(canEditItinerary);
        }
        saveSharing.setVisible(canManagePeople);
        saveSharing.setEnabled(canManagePeople);
    }

    private void refreshSharing(TripOptionsState state) {
        if (account == null || !state.hasActiveTrip()) {
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
                empty.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
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
                empty.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
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

    private boolean isSelfLockedMember(String userId) {
        return canManagePeople
                && currentUserId != null
                && currentUserId.equals(userId)
                && (ownerUser == null || !currentUserId.equals(ownerUser.getId()));
    }

    private void renderFeedback(TripOptionsState state) {
        feedback.setText(state.getMessage());
        feedback.setForeground(state.isError() ? SwingTheme.ERROR : SwingTheme.SUCCESS);
    }

    private static void setTimeSelectorEnabled(TimeSelectorPanel panel, boolean enabled) {
        panel.setEnabled(enabled);
        for (java.awt.Component component : panel.getComponents()) {
            component.setEnabled(enabled);
        }
    }

    private static JLabel boldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SwingTheme.BODY.deriveFont(java.awt.Font.BOLD));
        label.setForeground(SwingTheme.NAVY);
        return label;
    }

    private static void addField(JPanel panel, int row, String name, java.awt.Component value) {
        JLabel label = new JLabel(name);
        label.setFont(SwingTheme.BODY);
        label.setForeground(SwingTheme.NAVY);
        panel.add(label, constraints(0, row));
        GridBagConstraints valueConstraints = constraints(1, row);
        valueConstraints.weightx = 1;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(value, valueConstraints);
    }

    private static GridBagConstraints constraints(int column, int row) {
        GridBagConstraints result = new GridBagConstraints();
        result.gridx = column;
        result.gridy = row;
        result.anchor = GridBagConstraints.WEST;
        result.insets = new Insets(6, 6, 6, 6);
        return result;
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
            // Protect the selected value from platform-specific combo-box arrow widths.
            // Without an explicit width Aqua truncates "Edit" and Windows may show only "...".
            Dimension roleSize = new Dimension(112, 32);
            roleBox.setPreferredSize(roleSize);
            roleBox.setMinimumSize(roleSize);
            if (selfLocked) {
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
