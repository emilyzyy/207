package views;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import entity.entities.Friendship;
import entity.entities.User;
import interface_adapter.controllers.FriendsController;
import interface_adapter.viewmodels.FriendsState;
import interface_adapter.viewmodels.FriendsViewModel;

/** Friends hub View: delegates all actions to {@link FriendsController}. */
public final class FriendsDialog extends JDialog {
    private final FriendsController controller;
    private final FriendsViewModel viewModel;
    private final JLabel status = new JLabel(" ");
    private final JPanel addPanel = new JPanel(new BorderLayout(0, 8));
    private final JPanel requestsPanel = new JPanel();
    private final JPanel friendsPanel = new JPanel();
    private final JTextField usernameField = new JTextField(18);

    public FriendsDialog(JFrame owner, FriendsController controller, FriendsViewModel viewModel) {
        super(owner, "Friends", true);
        if (controller == null || viewModel == null) {
            throw new IllegalArgumentException("Friends controller and ViewModel are required");
        }
        this.controller = controller;
        this.viewModel = viewModel;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(460, 420));
        setPreferredSize(new Dimension(520, 480));

        final JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        root.setBackground(SwingTheme.PANEL);

        final JLabel title = new JLabel("Friends");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        root.add(title, BorderLayout.NORTH);

        final JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(SwingTheme.BODY);
        buildAddTab();
        requestsPanel.setLayout(new BoxLayout(requestsPanel, BoxLayout.Y_AXIS));
        requestsPanel.setBackground(SwingTheme.PANEL);
        friendsPanel.setLayout(new BoxLayout(friendsPanel, BoxLayout.Y_AXIS));
        friendsPanel.setBackground(SwingTheme.PANEL);
        tabs.addTab("Add", wrapScroll(addPanel));
        tabs.addTab("Requests", wrapScroll(requestsPanel));
        tabs.addTab("Friends", wrapScroll(friendsPanel));
        root.add(tabs, BorderLayout.CENTER);

        status.setFont(SwingTheme.SMALL);
        status.setForeground(SwingTheme.MUTED);
        final JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(status, BorderLayout.CENTER);
        final JButton close = SwingTheme.primaryButton("Close");
        close.addActionListener(event -> dispose());
        final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(close);
        footer.add(right, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        viewModel.addPropertyChangeListener(event -> render(viewModel.getState()));
        controller.load();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildAddTab() {
        addPanel.setOpaque(false);
        addPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        final JLabel help = new JLabel("Send a friend request using a unique username.");
        help.setFont(SwingTheme.BODY);
        help.setForeground(SwingTheme.MUTED);
        addPanel.add(help, BorderLayout.NORTH);

        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.add(new JLabel("Username"));
        row.add(usernameField);
        final JButton send = SwingTheme.primaryButton("Send request");
        send.addActionListener(event -> {
            controller.sendRequest(usernameField.getText());
            if (!viewModel.getState().isError()) {
                usernameField.setText("");
            }
        });
        row.add(send);
        addPanel.add(row, BorderLayout.CENTER);
    }

    private void render(FriendsState state) {
        status.setText(state.getMessage().isEmpty() ? " " : state.getMessage());
        status.setForeground(state.isError() ? SwingTheme.ERROR : SwingTheme.SUCCESS);
        if (!state.isError() && state.getMessage().isEmpty()) {
            status.setForeground(SwingTheme.MUTED);
        }
        rebuildRequests(state);
        rebuildFriends(state);
    }

    private void rebuildRequests(FriendsState state) {
        requestsPanel.removeAll();
        requestsPanel.add(sectionLabel("Incoming"));
        if (state.getIncoming().isEmpty()) {
            requestsPanel.add(mutedRow("No incoming requests."));
        } else {
            for (Friendship request : state.getIncoming()) {
                requestsPanel.add(incomingRow(request));
                requestsPanel.add(Box.createVerticalStrut(6));
            }
        }
        requestsPanel.add(Box.createVerticalStrut(12));
        requestsPanel.add(sectionLabel("Outgoing"));
        if (state.getOutgoing().isEmpty()) {
            requestsPanel.add(mutedRow("No outgoing requests."));
        } else {
            for (Friendship request : state.getOutgoing()) {
                requestsPanel.add(outgoingRow(request));
                requestsPanel.add(Box.createVerticalStrut(6));
            }
        }
        requestsPanel.revalidate();
        requestsPanel.repaint();
    }

    private void rebuildFriends(FriendsState state) {
        friendsPanel.removeAll();
        if (state.getAccepted().isEmpty()) {
            friendsPanel.add(mutedRow("You have no friends yet."));
        } else {
            for (Friendship friendship : state.getAccepted()) {
                friendsPanel.add(friendRow(friendship));
                friendsPanel.add(Box.createVerticalStrut(6));
            }
        }
        friendsPanel.revalidate();
        friendsPanel.repaint();
    }

    private JPanel incomingRow(Friendship request) {
        final JPanel row = listRow();
        row.add(avatarAndName(request.getOtherUser()), BorderLayout.CENTER);
        final JButton accept = SwingTheme.primaryButton("Accept");
        accept.addActionListener(event -> controller.accept(request.getId()));
        row.add(accept, BorderLayout.EAST);
        return row;
    }

    private JPanel outgoingRow(Friendship request) {
        final JPanel row = listRow();
        row.add(avatarAndName(request.getOtherUser()), BorderLayout.CENTER);
        final JButton cancel = SwingTheme.secondaryButton("Cancel");
        cancel.addActionListener(event -> controller.cancel(request.getId()));
        row.add(cancel, BorderLayout.EAST);
        return row;
    }

    private JPanel friendRow(Friendship friendship) {
        final User friend = friendship.getOtherUser();
        final JPanel row = listRow();
        row.add(avatarAndName(friend), BorderLayout.CENTER);
        final JButton remove = SwingTheme.secondaryButton("Remove");
        remove.addActionListener(event -> {
            final int choice = JOptionPane.showConfirmDialog(
                    FriendsDialog.this,
                    "Remove @" + friend.getUsername() + " from your friends?\n"
                            + "You can send them a new request later if you change your mind.",
                    "Remove friend",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                controller.remove(friendship.getId());
            }
        });
        row.add(remove, BorderLayout.EAST);
        return row;
    }

    private static JPanel listRow() {
        final JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(true);
        row.setBackground(SwingTheme.BACKGROUND);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SwingTheme.LINE),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private static JPanel avatarAndName(User user) {
        final JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        info.setOpaque(false);
        info.add(new JLabel(AvatarSupport.iconFor(user, 32)));
        final JLabel name = new JLabel("@" + user.getUsername());
        name.setFont(SwingTheme.BODY);
        name.setForeground(SwingTheme.NAVY);
        info.add(name);
        return info;
    }

    private static JLabel sectionLabel(String text) {
        final JLabel label = new JLabel(text);
        label.setFont(SwingTheme.BODY.deriveFont(java.awt.Font.BOLD));
        label.setForeground(SwingTheme.NAVY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 0));
        return label;
    }

    private static JLabel mutedRow(String text) {
        final JLabel label = new JLabel(text);
        label.setFont(SwingTheme.SMALL);
        label.setForeground(SwingTheme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return label;
    }

    private static JScrollPane wrapScroll(JPanel panel) {
        final JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return scroll;
    }
}
