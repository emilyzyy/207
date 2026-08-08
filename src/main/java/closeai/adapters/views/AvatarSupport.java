package closeai.adapters.views;

import closeai.domain.entities.User;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Circular avatar rendering and image helpers for profile UI. */
public final class AvatarSupport {
    public static final Color[] SOLID_COLORS = {
            Color.WHITE,
            new Color(31, 104, 225),
            new Color(13, 35, 64),
            new Color(26, 127, 83),
            new Color(181, 56, 48),
            new Color(146, 94, 6),
            new Color(91, 106, 123),
            new Color(156, 39, 176)
    };

    private AvatarSupport() {
    }

    public static String toHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static Color fromHex(String hex) {
        if (hex == null || hex.trim().isEmpty()) {
            return Color.WHITE;
        }
        String value = hex.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        try {
            return new Color(Integer.parseInt(value, 16));
        } catch (NumberFormatException exception) {
            return Color.WHITE;
        }
    }

    public static Icon iconFor(User user, int size) {
        if (user != null && user.hasUploadedAvatar()) {
            BufferedImage image = decodeImage(user.getAvatarImage());
            if (image != null) {
                return new CircularImageIcon(image, size);
            }
        }
        Color color = fromHex(user == null ? User.DEFAULT_AVATAR_COLOR : user.getAvatarColor());
        return new CircularColorIcon(color, size);
    }

    public static Icon iconFor(String avatarColor, String avatarImage, int size) {
        if (avatarImage != null && !avatarImage.trim().isEmpty()) {
            BufferedImage image = decodeImage(avatarImage);
            if (image != null) {
                return new CircularImageIcon(image, size);
            }
        }
        return new CircularColorIcon(fromHex(avatarColor), size);
    }

    public static JButton avatarButton(User user, int size) {
        JButton button = new JButton(iconFor(user, size));
        button.setPreferredSize(new Dimension(size + 4, size + 4));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setToolTipText(user == null ? "Profile" : user.getUsername());
        return button;
    }

    public static String chooseImageBase64(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a profile photo");
        chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif", "webp"));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(chooser.getSelectedFile().toPath());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalStateException("Could not read that image file.");
            }
            BufferedImage scaled = scaleToSquare(image, 128);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load the image: " + exception.getMessage(),
                    exception);
        }
    }

    public static BufferedImage decodeImage(String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            return null;
        }
        try {
            String data = base64.trim();
            int comma = data.indexOf(',');
            if (data.startsWith("data:") && comma > 0) {
                data = data.substring(comma + 1);
            }
            byte[] bytes = Base64.getDecoder().decode(data);
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IllegalArgumentException | IOException exception) {
            return null;
        }
    }

    private static BufferedImage scaleToSquare(BufferedImage source, int size) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        Image cropped = source.getSubimage(x, y, side, side)
                .getScaledInstance(size, size, Image.SCALE_SMOOTH);
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(cropped, 0, 0, null);
        g.dispose();
        return out;
    }

    private static final class CircularColorIcon implements Icon {
        private final Color color;
        private final int size;

        CircularColorIcon(Color color, int size) {
            this.color = color;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fill(new Ellipse2D.Float(x, y, size, size));
            g2.setColor(SwingTheme.LINE);
            g2.draw(new Ellipse2D.Float(x, y, size - 1, size - 1));
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    private static final class CircularImageIcon implements Icon {
        private final BufferedImage image;
        private final int size;

        CircularImageIcon(BufferedImage image, int size) {
            this.image = image;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setClip(new Ellipse2D.Float(x, y, size, size));
            g2.drawImage(image, x, y, size, size, null);
            g2.setClip(null);
            g2.setColor(SwingTheme.LINE);
            g2.draw(new Ellipse2D.Float(x, y, size - 1, size - 1));
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
