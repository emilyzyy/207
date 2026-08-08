package trippy.adapters.views;

import trippy.adapters.viewmodels.ShareState;
import trippy.adapters.viewmodels.ShareViewModel;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Modeless share preview with an explicit clipboard action. */
public final class ShareDialog extends JDialog {
    private final ShareViewModel viewModel;
    private final JTextArea preview = new JTextArea();
    private final JLabel status = new JLabel();
    private final JButton copyButton = SwingTheme.primaryButton("Copy Itinerary");

    public ShareDialog(Frame owner, ShareViewModel viewModel) {
        super(owner, "Share Trip", false);
        if (viewModel == null) {
            throw new IllegalArgumentException("Share ViewModel is required");
        }
        this.viewModel = viewModel;
        setSize(560, 460);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 12));
        getContentPane().setBackground(SwingTheme.BACKGROUND);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setBackground(SwingTheme.PANEL);
        heading.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JLabel title = new JLabel("Share your itinerary");
        title.setFont(SwingTheme.TITLE);
        title.setForeground(SwingTheme.NAVY);
        heading.add(title, BorderLayout.WEST);
        add(heading, BorderLayout.NORTH);

        preview.setName("share-preview");
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setFont(SwingTheme.BODY);
        preview.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JScrollPane scroll = new JScrollPane(preview);
        scroll.setBorder(BorderFactory.createLineBorder(SwingTheme.LINE));
        add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout(12, 0));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        status.setFont(SwingTheme.SMALL);
        actions.add(status, BorderLayout.CENTER);
        copyButton.setName("copy-itinerary");
        copyButton.addActionListener(event -> copyToClipboard());
        actions.add(copyButton, BorderLayout.EAST);
        add(actions, BorderLayout.SOUTH);

        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> render(viewModel.getState()));
    }

    public ShareViewModel getViewModel() {
        return viewModel;
    }

    private void copyToClipboard() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(preview.getText()), null);
            status.setText("Copied to clipboard.");
            status.setForeground(SwingTheme.SUCCESS);
        } catch (IllegalStateException exception) {
            status.setText("Clipboard is busy. Please try again.");
            status.setForeground(SwingTheme.ERROR);
        }
    }

    private void render(ShareState state) {
        preview.setText(state.getShareText());
        preview.setCaretPosition(0);
        status.setText(state.getMessage());
        status.setForeground(state.isError() ? SwingTheme.ERROR : SwingTheme.MUTED);
        copyButton.setEnabled(state.canCopy());
    }
}
