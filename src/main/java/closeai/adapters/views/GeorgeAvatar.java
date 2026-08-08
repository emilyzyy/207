package closeai.adapters.views;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
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
        double scale = Math.min(
                (double) width / source.getWidth(),
                (double) height / source.getHeight());
        int scaledWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(
                source,
                (width - scaledWidth) / 2,
                (height - scaledHeight) / 2,
                scaledWidth,
                scaledHeight,
                null);
        graphics.dispose();
        return new ImageIcon(canvas);
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
