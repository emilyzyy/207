package closeai.adapters.views;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/** Loads the user-supplied George artwork while keeping a graceful packaged fallback. */
final class GeorgeAvatar {
    private GeorgeAvatar() {
    }

    static ImageIcon icon(int width, int height) {
        BufferedImage source = load();
        Image scaled = source.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private static BufferedImage load() {
        try (InputStream stream = GeorgeAvatar.class.getResourceAsStream(
                "/closeai/george-avatar.png.b64")) {
            if (stream == null) {
                return fallback();
            }
            String encoded = new String(readAll(stream), StandardCharsets.US_ASCII)
                    .replaceAll("\\s", "");
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(
                    Base64.getDecoder().decode(encoded)));
            return image == null ? fallback() : image;
        } catch (IOException | IllegalArgumentException exception) {
            return fallback();
        }
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = stream.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static BufferedImage fallback() {
        BufferedImage image = new BufferedImage(120, 84, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(130, 60, 35));
        graphics.fillOval(22, 5, 76, 74);
        graphics.setColor(new Color(246, 190, 134));
        graphics.fillOval(34, 19, 52, 45);
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 20));
        graphics.drawString("G", 52, 49);
        graphics.dispose();
        return image;
    }
}
