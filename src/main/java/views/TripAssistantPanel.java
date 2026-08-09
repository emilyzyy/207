package views;

import interface_adapter.controllers.TripAssistantController;
import interface_adapter.viewmodels.TripAssistantState;
import interface_adapter.viewmodels.TripAssistantViewModel;
import entity.valueobjects.TripAssistantMessage;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** George's trip-aware chat view. */
public final class TripAssistantPanel extends JPanel {
    private final TripAssistantViewModel viewModel;
    private final TripAssistantController controller;
    private final JTextArea history = new JTextArea();
    private final JTextField input = new JTextField();
    private final JButton send = SwingTheme.primaryButton("Send");
    private final JPanel loading = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JLabel error = new JLabel(" ");
    private final JScrollPane historyScroll;
    private final JButton minimize = headerButton("\u2212", "Minimize George chat");
    private Runnable collapseAction = () -> { };

    public TripAssistantPanel(
            TripAssistantViewModel viewModel, TripAssistantController controller) {
        if (viewModel == null || controller == null) {
            throw new IllegalArgumentException("Trip Assistant view dependencies are required");
        }
        this.viewModel = viewModel;
        this.controller = controller;
        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SwingTheme.LINE),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        add(header(), BorderLayout.NORTH);
        history.setEditable(false);
        history.setLineWrap(true);
        history.setWrapStyleWord(true);
        history.setFont(SwingTheme.BODY);
        history.setBackground(SwingTheme.BACKGROUND);
        history.setForeground(SwingTheme.NAVY);
        history.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        history.setToolTipText("Conversation with George");
        history.getAccessibleContext().setAccessibleName("George chat history");
        historyScroll = new JScrollPane(history);
        historyScroll.setBorder(BorderFactory.createLineBorder(SwingTheme.LINE));
        add(historyScroll, BorderLayout.CENTER);
        add(composer(), BorderLayout.SOUTH);

        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> render(viewModel.getState()));
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setOpaque(false);
        JLabel avatar = new JLabel(GeorgeAvatar.icon(52, 48));
        avatar.getAccessibleContext().setAccessibleName("George the trip assistant");
        panel.add(avatar, BorderLayout.WEST);

        JPanel words = new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("George · Trip Assistant");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        JLabel subtitle = new JLabel("Grounded in your current trip");
        subtitle.setFont(SwingTheme.SMALL);
        subtitle.setForeground(SwingTheme.MUTED);
        words.add(Box.createVerticalGlue());
        words.add(title);
        words.add(Box.createVerticalStrut(4));
        words.add(subtitle);
        words.add(Box.createVerticalGlue());
        panel.add(words, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        controls.setOpaque(false);
        minimize.addActionListener(event -> collapseAction.run());
        controls.add(minimize);
        panel.add(controls, BorderLayout.EAST);
        return panel;
    }

    private static JButton headerButton(String text, String accessibleName) {
        JButton button = new JButton(text);
        button.setFont(SwingTheme.BODY.deriveFont(java.awt.Font.BOLD, 16));
        button.setForeground(SwingTheme.MUTED);
        button.setBackground(SwingTheme.PANEL);
        button.setFocusPainted(false);
        button.setMargin(new java.awt.Insets(2, 14, 2, 14));
        button.setPreferredSize(new Dimension(48, 28));
        button.setMinimumSize(new Dimension(48, 28));
        button.setToolTipText(accessibleName);
        button.getAccessibleContext().setAccessibleName(accessibleName);
        return button;
    }

    private JPanel composer() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        loading.setOpaque(false);
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(90, 8));
        JLabel loadingText = new JLabel("George is checking your trip…");
        loadingText.setFont(SwingTheme.SMALL);
        loadingText.setForeground(SwingTheme.MUTED);
        loading.add(progress);
        loading.add(loadingText);
        panel.add(loading);

        error.setFont(SwingTheme.SMALL);
        error.setForeground(SwingTheme.ERROR);
        error.getAccessibleContext().setAccessibleName("Trip Assistant error");
        panel.add(error);
        panel.add(Box.createVerticalStrut(5));

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        input.setFont(SwingTheme.BODY);
        input.setToolTipText("Ask George about this trip");
        input.getAccessibleContext().setAccessibleName("Message George");
        send.setToolTipText("Send message to George");
        send.getAccessibleContext().setAccessibleName("Send message to George");
        input.addActionListener(event -> send());
        send.addActionListener(event -> send());
        row.add(input, BorderLayout.CENTER);
        row.add(send, BorderLayout.EAST);
        panel.add(row);
        return panel;
    }

    private void send() {
        String question = input.getText();
        if (!question.trim().isEmpty()) {
            input.setText("");
        }
        controller.execute(question);
    }

    private void render(TripAssistantState state) {
        StringBuilder text = new StringBuilder();
        for (TripAssistantMessage message : state.getMessages()) {
            text.append(message.getRole() == TripAssistantMessage.Role.USER ? "You" : "George")
                    .append(":\n").append(message.getText()).append("\n\n");
        }
        history.setText(text.toString().trim());
        loading.setVisible(state.isLoading());
        input.setEnabled(!state.isLoading());
        send.setEnabled(!state.isLoading());
        error.setText(state.getError().isEmpty() ? " " : state.getError());
        SwingUtilities.invokeLater(() -> historyScroll.getVerticalScrollBar().setValue(
                historyScroll.getVerticalScrollBar().getMaximum()));
    }

    public JTextArea getHistoryArea() { return history; }

    public JTextField getInputField() { return input; }

    public JButton getSendButton() { return send; }

    public boolean isLoadingVisible() { return loading.isVisible(); }

    public JLabel getErrorLabel() { return error; }

    public JButton getMinimizeButton() { return minimize; }

    public void setCollapseAction(Runnable action) {
        collapseAction = action == null ? () -> { } : action;
    }
}
