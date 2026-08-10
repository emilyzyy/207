package interface_adapter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.TripDay;
import entity.valueobjects.EventType;

/**
 * Renders each trip day as a shareable PNG card (no Swing components).
 * Multi-day trips produce one image per day.
 */
public final class DayPlanShareImageRenderer {
    private static final int WIDTH = 720;
    private static final int PAD = 36;
    private static final int ROW_HEIGHT = 56;
    private static final Color NAVY = new Color(13, 35, 64);
    private static final Color BLUE = new Color(31, 104, 225);
    private static final Color MUTED = new Color(91, 106, 123);
    private static final Color LINE = new Color(216, 224, 232);
    private static final Color BG = new Color(244, 247, 250);
    private static final Color PANEL = Color.WHITE;
    private static final Color TRAVEL = new Color(247, 249, 252);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.ENGLISH);

    private DayPlanShareImageRenderer() {
    }

    /** One PNG card per day, in trip order. */
    public static List<BufferedImage> renderTrip(Trip trip) {
        if (trip == null) {
            return Collections.emptyList();
        }
        final List<BufferedImage> images = new ArrayList<BufferedImage>();
        final int dayCount = trip.getDayCount();
        for (int i = 0; i < dayCount; i++) {
            images.add(renderDay(trip, i));
        }
        return images;
    }

    public static BufferedImage renderDay(Trip trip, int dayIndex) {
        final TripDay day = trip.getDay(dayIndex);
        final List<ScheduledEvent> events = day.getScheduledEvents();
        final int header = 150;
        int body = Math.max(ROW_HEIGHT, events.size() * ROW_HEIGHT);
        if (events.isEmpty()) {
            body = 80;
        }
        final int footer = 52;
        final int height = header + body + footer + PAD;

        final BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BG);
            g.fillRect(0, 0, WIDTH, height);

            final int cardX = 16;
            final int cardW = WIDTH - 32;
            g.setColor(PANEL);
            g.fillRoundRect(cardX, 16, cardW, height - 32, 18, 18);
            g.setColor(LINE);
            g.setStroke(new BasicStroke(1.2f));
            g.drawRoundRect(cardX, 16, cardW, height - 32, 18, 18);

            final int x = cardX + PAD - 8;
            int y = 48;
            g.setColor(NAVY);
            g.setFont(new Font("SansSerif", Font.BOLD, 26));
            g.drawString("Trippy · " + trip.getDestination(), x, y);

            y += 34;
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.setColor(BLUE);
            final String dayLabel = trip.getDayCount() > 1
                    ? "Day " + (dayIndex + 1) + " of " + trip.getDayCount()
                    : "Day plan";
            g.drawString(dayLabel, x, y);

            y += 26;
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.setColor(MUTED);
            g.drawString(day.getDate().format(DATE)
                    + "  ·  "
                    + day.getStartTime().format(TIME)
                    + " – "
                    + day.getEndTime().format(TIME)
                    + "  ·  "
                    + trip.getTransportationMode(), x, y);

            y += 28;
            g.setColor(LINE);
            g.drawLine(x, y, cardX + cardW - PAD + 8, y);
            y += 24;

            if (events.isEmpty()) {
                g.setColor(MUTED);
                g.setFont(new Font("SansSerif", Font.ITALIC, 14));
                g.drawString("No activities scheduled for this day yet.", x, y + 12);
            } else {
                for (ScheduledEvent event : events) {
                    final boolean travel = event.getEventType() == EventType.TRAVEL;
                    g.setColor(travel ? TRAVEL : new Color(238, 245, 255));
                    g.fillRoundRect(x - 8, y - 18, cardW - 2 * (PAD - 16), ROW_HEIGHT - 8, 12, 12);

                    g.setColor(travel ? MUTED : NAVY);
                    g.setFont(new Font("SansSerif", Font.BOLD, 13));
                    final String timeRange = event.getStartTime().format(TIME)
                            + " – "
                            + event.getEndTime().format(TIME);
                    g.drawString(timeRange, x, y + 4);

                    g.setFont(new Font("SansSerif", Font.PLAIN, 14));
                    g.setColor(NAVY);
                    String name = eventName(event);
                    final FontMetrics metrics = g.getFontMetrics();
                    final int maxNameWidth = cardW - 2 * PAD - 20;
                    if (metrics.stringWidth(name) > maxNameWidth) {
                        while (name.length() > 3
                                && metrics.stringWidth(name + "…") > maxNameWidth) {
                            name = name.substring(0, name.length() - 1);
                        }
                        name = name + "…";
                    }
                    g.drawString(name, x, y + 26);
                    y += ROW_HEIGHT;
                }
            }

            g.setColor(MUTED);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString("Shared from Trippy", x, height - 36);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static String eventName(ScheduledEvent event) {
        if (event.getEventType() == EventType.TRAVEL) {
            return event.getNotes() == null || event.getNotes().trim().isEmpty()
                    ? "Travel" : event.getNotes().trim();
        }
        return event.getActivity() == null
                ? event.getEventType().toString()
                : event.getActivity().getName();
    }
}
