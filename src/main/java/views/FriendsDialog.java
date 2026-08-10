package views;

import use_case.ports.AccountService;
import entity.entities.Friendship;
import entity.entities.User;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
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

/** Friends hub: add by username, manage requests, and view current friends. */
public final class FriendsDialog extends JDialog {
    private final AccountService account;
    private final JLabel status = new JLabel(" ");
    private final JPanel addPanel = new JPanel(new BorderLayout(0, 8));
    private final JPanel requestsPanel = new JPanel();
    private final JPanel friendsPanel = new JPanel();
    private final JTextField usernameField = new JTextField(18);

    public FriendsDialog(JFrame owner, AccountService account) {
        super(owner, "Friends", true);
        if (account == null) {
            throw new IllegalArgumentException("Account service is required");
        }
        this.account = account;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(460, 420));
        setPreferredSize(new Dimension(520, 480));

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        root.setBackground(SwingTheme.PANEL);

        JLabel title = new JLabel("Friends");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        root.add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(SwingTheme.BODY);
        buildAddTab();
        requestsPanel.setLayout(new BoxLayout(requestsPanel, BoxLayout.Y_AXIS));
        requestsPanel.setBackground(SwingTheme.PANEL);
        friendsPanel.setLayout(new BoxLayout(friendsPanel, BoxLayout.Y_AXIS));
        friendsPanel.setBackground(SwingTheme.PANEL);
        tabs.addTab("Add", wrapScroll(addPanel));
        tabs.addTab("Requests", wrapScroll(requestsPanel));
        tabs.addTab("Friends", wrapScroll(friendsPanel));
        tabs.addChangeListener(event -> {
            if (tabs.getSelectedIndex() == 1) {
                refreshRequests();
            } else if (tabs.getSelectedIndex() == 2) {
                refreshFriends();
            }
        });
        root.add(tabs, BorderLayout.CENTER);

        status.setFont(SwingTheme.SMALL);
        status.setForeground(SwingTheme.MUTED);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(status, BorderLayout.CENTER);
        JButton close = SwingTheme.primaryButton("Close");
        close.addActionListener(event -> dispose());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(close);
        footer.add(right, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        refreshRequests();
        refreshFriends();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildAddTab() {
        addPanel.setOpaque(false);
        addPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel help = new JLabel("Send a friend request using a unique username.");
        help.setFont(SwingTheme.BODY);
        help.setForeground(SwingTheme.MUTED);
        addPanel.add(help, BorderLayout.NORTH);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.add(new JLabel("Username"));
        row.add(usernameField);
        JButton send = SwingTheme.primaryButton("Send request");
        send.addActionListener(event -> sendRequest());
        row.add(send);
        addPanel.add(row, BorderLayout.CENTER);
    }

    private void sendRequest() {
        try {
            Friendship created = account.sendFriendRequest(usernameField.getText().trim());
            usernameField.setText("");
            status.setForeground(SwingTheme.SUCCESS);
            status.setText("Request sent to @" + created.getOtherUser().getUsername() + ".");
            refreshRequests();
        } catch (RuntimeException exception) {
            status.setForeground(SwingTheme.ERROR);
            status.setText(exception.getMessage());
        }
    }

    private void refreshRequests() {
        requestsPanel.removeAll();
        try {
            List<Friendship> incoming = account.listIncomingRequests();
            List<Friendship> outgoing = account.listOutgoingRequests();
            requestsPanel.add(sectionLabel("Incoming"));
            if (incoming.isEmpty()) {
                requestsPanel.add(mutedRow("No incoming requests."));
            } else {
                for (Friendship request : incoming) {
                    requestsPanel.add(incomingRow(request));
                    requestsPanel.add(Box.createVerticalStrut(6));
                }
            }
            requestsPanel.add(Box.createVerticalStrut(12));
            requestsPanel.add(sectionLabel("Outgoing"));
            if (outgoing.isEmpty()) {
                requestsPanel.add(mutedRow("No outgoing requests."));
            } else {
                for (Friendship request : outgoing) {
                    requestsPanel.add(outgoingRow(request));
                    requestsPanel.add(Box.createVerticalStrut(6));
                }
            }
        } catch (RuntimeException exception) {
            status.setForeground(SwingTheme.ERROR);
            status.setText(exception.getMessage());
            requestsPanel.add(mutedRow("Could not load requests."));
        }
        requestsPanel.revalidate();
        requestsPanel.repaint();
    }

    private void refreshFriends() {
        friendsPanel.removeAll();
        try {
            List<Friendship> friends = account.listAcceptedFriendships();
            if (friends.isEmpty()) {
                friendsPanel.add(mutedRow("You have no friends yet."));
            } else {
                for (Friendship friendship : friends) {
                    friendsPanel.add(friendRow(friendship));
                    friendsPanel.add(Box.createVerticalStrut(6));
                }
            }
        } catch (RuntimeException exception) {
            status.setForeground(SwingTheme.ERROR);
            status.setText(exception.getMessage());
            friendsPanel.add(mutedRow("Could not load friends."));
        }
        friendsPanel.revalidate();
        friendsPanel.repaint();
    }

    private JPanel incomingRow(Friendship request) {
        JPanel row = listRow();
        row.add(avatarAndName(request.getOtherUser()), BorderLayout.CENTER);
        JButton accept = SwingTheme.primaryButton("Accept");
        accept.addActionListener(event -> {
            try {
                account.acceptFriendRequest(request.getId());
                status.setForeground(SwingTheme.SUCCESS);
                status.setText("You are now friends with @" + request.getOtherUser().getUsername() + ".");
                refreshRequests();
                refreshFriends();
            } catch (RuntimeException exception) {
                status.setForeground(SwingTheme.ERROR);
                status.setText(exception.getMessage());
            }
        });
        row.add(accept, BorderLayout.EAST);
        return row;
    }

    private JPanel outgoingRow(Friendship request) {
        JPanel row = listRow();
        row.add(avatarAndName(request.getOtherUser()), BorderLayout.CENTER);
        JButton cancel = SwingTheme.secondaryButton("Cancel");
        cancel.addActionListener(event -> {
            try {
                account.cancelFriendRequest(request.getId());
                status.setForeground(SwingTheme.MUTED);
                status.setText("Cancelled request to @" + request.getOtherUser().getUsername() + ".");
                refreshRequests();
            } catch (RuntimeException exception) {
                status.setForeground(SwingTheme.ERROR);
                status.setText(exception.getMessage());
            }
        });
        row.add(cancel, BorderLayout.EAST);
        return row;
    }

    private JPanel friendRow(Friendship friendship) {
        User friend = friendship.getOtherUser();
        JPanel row = listRow();
        row.add(avatarAndName(friend), BorderLayout.CENTER);
        JButton remove = SwingTheme.secondaryButton("Remove");
        remove.addActionListener(event -> {
            int choice = JOptionPane.showConfirmDialog(
                    FriendsDialog.this,
                    "Remove @" + friend.getUsername() + " from your friends?\n"
                            + "You can send them a new request later if you change your mind.",
                    "Remove friend",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                account.removeFriend(friendship.getId());
                status.setForeground(SwingTheme.MUTED);
                status.setText("Removed @" + friend.getUsername()
                        + ". You can send them a new request anytime.");
                refreshFriends();
            } catch (RuntimeException exception) {
                status.setForeground(SwingTheme.ERROR);
                status.setText(exception.getMessage());
            }
        });
        row.add(remove, BorderLayout.EAST);
        return row;
    }

    private static JPanel listRow() {
        JPanel row = new JPanel(new BorderLayout(10, 0));
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
        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        info.setOpaque(false);
        info.add(new JLabel(AvatarSupport.iconFor(user, 32)));
        JLabel name = new JLabel("@" + user.getUsername());
        name.setFont(SwingTheme.BODY);
        name.setForeground(SwingTheme.NAVY);
        info.add(name);
        return info;
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SwingTheme.BODY.deriveFont(java.awt.Font.BOLD));
        label.setForeground(SwingTheme.NAVY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 0));
        return label;
    }

    private static JLabel mutedRow(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SwingTheme.SMALL);
        label.setForeground(SwingTheme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return label;
    }

    private static JScrollPane wrapScroll(JPanel panel) {
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return scroll;
    }
}
