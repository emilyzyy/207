package closeai.infrastructure.export;

import closeai.application.ports.ItineraryPngExporter;
import closeai.application.usecases.ShareCardModel;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Renders a shareable itinerary card with AWT Graphics2D and ImageIO. */
public final class AwtItineraryPngExporter implements ItineraryPngExporter {
    private static final int WIDTH = 720;
    private static final int MARGIN = 36;
    private static final int LINE_GAP = 10;
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US);

    private static final Color NAVY = new Color(13, 35, 64);
    private static final Color BLUE = new Color(31, 104, 225);
    private static final Color MUTED = new Color(91, 106, 123);
    private static final Color LINE = new Color(216, 224, 232);
    private static final Color PANEL = Color.WHITE;
    private static final Color TRAVEL = new Color(120, 140, 160);

    @Override
    public byte[] export(ShareCardModel card) {
        if (card == null) {
            throw new IllegalArgumentException("Share card is required");
        }

        Font brandFont = new Font("SansSerif", Font.BOLD, 22);
        Font titleFont = new Font("SansSerif", Font.BOLD, 28);
        Font metaFont = new Font("SansSerif", Font.PLAIN, 15);
        Font timeFont = new Font("SansSerif", Font.BOLD, 14);
        Font eventFont = new Font("SansSerif", Font.PLAIN, 16);

        int height = estimateHeight(card, brandFont, titleFont, metaFont, timeFont, eventFont);
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(PANEL);
            g.fillRect(0, 0, WIDTH, height);

            int y = MARGIN;
            g.setFont(brandFont);
            g.setColor(BLUE);
            g.drawString("CloseAI", MARGIN, y + g.getFontMetrics().getAscent());
            y += g.getFontMetrics().getHeight() + 8;

            g.setFont(titleFont);
            g.setColor(NAVY);
            String title = card.getDestination() + " day trip";
            g.drawString(title, MARGIN, y + g.getFontMetrics().getAscent());
            y += g.getFontMetrics().getHeight() + 6;

            g.setFont(metaFont);
            g.setColor(MUTED);
            String meta = DATE.format(card.getDate()) + "  ·  "
                    + readableMode(card.getTransportationMode());
            g.drawString(meta, MARGIN, y + g.getFontMetrics().getAscent());
            y += g.getFontMetrics().getHeight() + 18;

            g.setColor(LINE);
            g.fillRect(MARGIN, y, WIDTH - 2 * MARGIN, 2);
            y += 20;

            for (ShareCardModel.ShareCardLine line : card.getLines()) {
                g.setFont(timeFont);
                g.setColor(line.isTravel() ? TRAVEL : BLUE);
                g.drawString(line.getTimeRange(), MARGIN, y + g.getFontMetrics().getAscent());
                y += g.getFontMetrics().getHeight() + 2;

                g.setFont(eventFont);
                g.setColor(line.isTravel() ? MUTED : NAVY);
                for (String wrapped : wrap(g, line.getTitle(), WIDTH - 2 * MARGIN)) {
                    g.drawString(wrapped, MARGIN, y + g.getFontMetrics().getAscent());
                    y += g.getFontMetrics().getHeight();
                }
                y += LINE_GAP + 6;
            }

            y += 8;
            g.setColor(LINE);
            g.fillRect(MARGIN, y, WIDTH - 2 * MARGIN, 1);
            y += 16;
            g.setFont(metaFont);
            g.setColor(MUTED);
            g.drawString("Shared from CloseAI", MARGIN, y + g.getFontMetrics().getAscent());
        } finally {
            g.dispose();
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("PNG writer is unavailable");
            }
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode share PNG", exception);
        }
    }

    private static int estimateHeight(
            ShareCardModel card, Font brandFont, Font titleFont, Font metaFont,
            Font timeFont, Font eventFont) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = probe.createGraphics();
        try {
            int y = MARGIN;
            y += metrics(g, brandFont).getHeight() + 8;
            y += metrics(g, titleFont).getHeight() + 6;
            y += metrics(g, metaFont).getHeight() + 18;
            y += 22;
            for (ShareCardModel.ShareCardLine line : card.getLines()) {
                y += metrics(g, timeFont).getHeight() + 2;
                g.setFont(eventFont);
                y += wrap(g, line.getTitle(), WIDTH - 2 * MARGIN).size()
                        * metrics(g, eventFont).getHeight();
                y += LINE_GAP + 6;
            }
            y += 8 + 16 + metrics(g, metaFont).getHeight() + MARGIN;
            return Math.max(y, 320);
        } finally {
            g.dispose();
        }
    }

    private static FontMetrics metrics(Graphics2D g, Font font) {
        g.setFont(font);
        return g.getFontMetrics();
    }

    private static java.util.List<String> wrap(Graphics2D g, String text, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        FontMetrics metrics = g.getFontMetrics();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (metrics.stringWidth(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (current.length() > 0) {
                    lines.add(current.toString());
                }
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        if (lines.isEmpty()) {
            lines.add(text);
        }
        return lines;
    }

    private static String readableMode(String mode) {
        if (mode == null || mode.isEmpty()) {
            return "Walking";
        }
        String lower = mode.toLowerCase(Locale.US);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
