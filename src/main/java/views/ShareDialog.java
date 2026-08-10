package views;

import interface_adapter.viewmodels.ShareState;
import interface_adapter.viewmodels.ShareViewModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

/** Modeless share preview: scrollable day-plan PNGs plus text copy / save. */
public final class ShareDialog extends JDialog {
    private final ShareViewModel viewModel;
    private final JPanel imagesPanel = new JPanel();
    private final JTextArea preview = new JTextArea();
    private final JLabel status = new JLabel();
    private final JButton copyTextButton = SwingTheme.secondaryButton("Copy text");
    private final JButton copyImageButton = SwingTheme.secondaryButton("Copy image");
    private final JButton saveButton = SwingTheme.primaryButton("Save PNG(s)…");

    public ShareDialog(Frame owner, ShareViewModel viewModel) {
        super(owner, "Share Trip", false);
        if (viewModel == null) {
            throw new IllegalArgumentException("Share ViewModel is required");
        }
        this.viewModel = viewModel;
        setSize(640, 720);
        setMinimumSize(new Dimension(520, 520));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 12));
        getContentPane().setBackground(SwingTheme.BACKGROUND);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setBackground(SwingTheme.PANEL);
        heading.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JLabel title = new JLabel("Share your day plan");
        title.setFont(SwingTheme.TITLE);
        title.setForeground(SwingTheme.NAVY);
        heading.add(title, BorderLayout.WEST);
        JLabel hint = new JLabel("Scroll to see each day, then save or copy.");
        hint.setFont(SwingTheme.SMALL);
        hint.setForeground(SwingTheme.MUTED);
        heading.add(hint, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);

        imagesPanel.setLayout(new BoxLayout(imagesPanel, BoxLayout.Y_AXIS));
        imagesPanel.setBackground(SwingTheme.BACKGROUND);
        imagesPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JScrollPane imageScroll = new JScrollPane(imagesPanel);
        imageScroll.setBorder(BorderFactory.createLineBorder(SwingTheme.LINE));
        imageScroll.getVerticalScrollBar().setUnitIncrement(24);
        imageScroll.setPreferredSize(new Dimension(600, 420));

        preview.setName("share-preview");
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setFont(SwingTheme.BODY);
        preview.setRows(5);
        preview.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane textScroll = new JScrollPane(preview);
        textScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(SwingTheme.LINE), "Text summary"));

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        center.add(imageScroll, BorderLayout.CENTER);
        center.add(textScroll, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout(12, 0));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));
        status.setFont(SwingTheme.SMALL);
        actions.add(status, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        copyTextButton.setName("copy-itinerary");
        copyTextButton.addActionListener(event -> copyTextToClipboard());
        copyImageButton.setName("copy-day-image");
        copyImageButton.addActionListener(event -> copyFirstImageToClipboard());
        saveButton.setName("save-day-images");
        saveButton.addActionListener(event -> saveImages());
        buttons.add(copyTextButton);
        buttons.add(copyImageButton);
        buttons.add(saveButton);
        actions.add(buttons, BorderLayout.EAST);
        add(actions, BorderLayout.SOUTH);

        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> render(viewModel.getState()));
    }

    public ShareViewModel getViewModel() {
        return viewModel;
    }

    private void copyTextToClipboard() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(preview.getText()), null);
            status.setText("Text copied to clipboard.");
            status.setForeground(SwingTheme.SUCCESS);
        } catch (IllegalStateException exception) {
            status.setText("Clipboard is busy. Please try again.");
            status.setForeground(SwingTheme.ERROR);
        }
    }

    private void copyFirstImageToClipboard() {
        List<BufferedImage> images = viewModel.getState().getDayImages();
        if (images.isEmpty()) {
            status.setText("No day-plan image to copy yet.");
            status.setForeground(SwingTheme.ERROR);
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new ImageSelection(images.get(0)), null);
            String note = images.size() > 1
                    ? "Copied day 1 image. Save PNG(s) to share every day."
                    : "Day-plan image copied — paste into Messages, Mail, or Slack.";
            status.setText(note);
            status.setForeground(SwingTheme.SUCCESS);
        } catch (IllegalStateException exception) {
            status.setText("Clipboard is busy. Please try again.");
            status.setForeground(SwingTheme.ERROR);
        }
    }

    private void saveImages() {
        List<BufferedImage> images = viewModel.getState().getDayImages();
        if (images.isEmpty()) {
            status.setText("No day-plan images to save.");
            status.setForeground(SwingTheme.ERROR);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(images.size() == 1
                ? "Save day-plan PNG"
                : "Choose folder for day-plan PNGs");
        if (images.size() == 1) {
            chooser.setSelectedFile(new File("trippy-day-plan.png"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getParentFile(), file.getName() + ".png");
            }
            try {
                ImageIO.write(images.get(0), "png", file);
                status.setText("Saved " + file.getName());
                status.setForeground(SwingTheme.SUCCESS);
            } catch (IOException exception) {
                status.setText("Could not save PNG: " + exception.getMessage());
                status.setForeground(SwingTheme.ERROR);
            }
            return;
        }
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File folder = chooser.getSelectedFile();
        try {
            for (int i = 0; i < images.size(); i++) {
                File file = new File(folder, "trippy-day-" + (i + 1) + ".png");
                ImageIO.write(images.get(i), "png", file);
            }
            status.setText("Saved " + images.size() + " PNGs in " + folder.getName());
            status.setForeground(SwingTheme.SUCCESS);
        } catch (IOException exception) {
            status.setText("Could not save PNGs: " + exception.getMessage());
            status.setForeground(SwingTheme.ERROR);
        }
    }

    private void render(ShareState state) {
        preview.setText(state.getShareText());
        preview.setCaretPosition(0);
        status.setText(state.getMessage());
        status.setForeground(state.isError() ? SwingTheme.ERROR : SwingTheme.MUTED);
        copyTextButton.setEnabled(state.canCopy());
        copyImageButton.setEnabled(state.canSaveImages());
        saveButton.setEnabled(state.canSaveImages());
        rebuildImages(state.getDayImages());
    }

    private void rebuildImages(List<BufferedImage> images) {
        imagesPanel.removeAll();
        if (images == null || images.isEmpty()) {
            JLabel empty = new JLabel("Share a trip to see day-plan images here.",
                    SwingConstants.CENTER);
            empty.setFont(SwingTheme.BODY);
            empty.setForeground(SwingTheme.MUTED);
            empty.setAlignmentX(LEFT_ALIGNMENT);
            imagesPanel.add(empty);
        } else {
            for (int i = 0; i < images.size(); i++) {
                BufferedImage image = images.get(i);
                if (images.size() > 1) {
                    JLabel caption = new JLabel("Day " + (i + 1));
                    caption.setFont(SwingTheme.HEADING);
                    caption.setForeground(SwingTheme.NAVY);
                    caption.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
                    caption.setAlignmentX(LEFT_ALIGNMENT);
                    imagesPanel.add(caption);
                }
                JLabel picture = new JLabel(new ImageIcon(scaleForPreview(image)));
                picture.setAlignmentX(LEFT_ALIGNMENT);
                picture.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(SwingTheme.LINE),
                        BorderFactory.createEmptyBorder(4, 4, 4, 4)));
                imagesPanel.add(picture);
                imagesPanel.add(Box.createVerticalStrut(12));
            }
        }
        imagesPanel.revalidate();
        imagesPanel.repaint();
    }

    private static BufferedImage scaleForPreview(BufferedImage source) {
        int maxWidth = 560;
        if (source.getWidth() <= maxWidth) {
            return source;
        }
        double scale = maxWidth / (double) source.getWidth();
        int width = maxWidth;
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return scaled;
    }

    private static final class ImageSelection implements Transferable {
        private final BufferedImage image;

        private ImageSelection(BufferedImage image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
