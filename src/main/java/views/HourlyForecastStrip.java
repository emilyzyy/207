package views;

import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import entity.entities.WeatherWarning;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * The hourly forecast as a flat strip of hour cards, made to float inside the dashboard.
 *
 * <p>This replaces the modeless {@code HourlyWeatherDialog} window. The forecast is glance
 * material — "when does the rain start?" — and a separate window is a heavy answer to a
 * glance question. As a strip it sits just above the weather bar, covers a sliver of the
 * map, and disappears with the same click that summoned it.</p>
 *
 * <p>One card per hour: time, condition glyph, temperature, precipitation. A few hours show
 * at once and the rest of the day is reached by scrolling sideways — the mouse wheel is
 * translated to horizontal scrolling, since the strip has no vertical dimension to spend.
 * It reads the same {@link DayPlanViewModel} the dialog read, so it updates live when the
 * forecast loads or changes, and it starts scrolled to the first daytime hour because
 * nobody opens a forecast to learn about 2 a.m.</p>
 */
public final class HourlyForecastStrip extends JPanel {

    /** Short on purpose: the strip borrows map space and must give most of it back. */
    static final int STRIP_HEIGHT = 104;

    /** What a card actually needs; anything more becomes a gap above the scrollbar. */
    private static final int CARD_HEIGHT = 78;

    private static final int CARD_WIDTH = 66;

    /** Well under a card, so a notch nudges rather than flings. */
    private static final int WHEEL_STEP = 26;

    private static final int SCROLLBAR_THICKNESS = 6;
    private static final DateTimeFormatter HOUR_FORMAT =
            DateTimeFormatter.ofPattern("h a", Locale.ENGLISH);
    private static final LocalTime FIRST_INTERESTING_HOUR = LocalTime.of(8, 0);

    private final DayPlanViewModel dayPlanViewModel;
    private final JPanel cards = new JPanel();
    private final JScrollPane scroller;
    private final PropertyChangeListener dayPlanListener = event -> refreshSafely();
    private boolean listening = true;

    public HourlyForecastStrip(DayPlanViewModel dayPlanViewModel) {
        if (dayPlanViewModel == null) {
            throw new IllegalArgumentException("Day Plan ViewModel is required");
        }
        this.dayPlanViewModel = dayPlanViewModel;

        setLayout(new BorderLayout());
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SwingTheme.LINE, 1, true),
                BorderFactory.createEmptyBorder(6, 8, 0, 8)));
        getAccessibleContext().setAccessibleName("Hourly forecast");

        cards.setLayout(new BoxLayout(cards, BoxLayout.X_AXIS));
        cards.setOpaque(false);

        scroller = new JScrollPane(cards,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        javax.swing.JScrollBar bar = scroller.getHorizontalScrollBar();
        bar.setUnitIncrement(WHEEL_STEP);
        bar.setPreferredSize(new Dimension(0, SCROLLBAR_THICKNESS));
        bar.setUI(new QuietScrollBarUI());
        bar.setOpaque(false);
        // The strip has no vertical dimension, so the wheel means "sideways" here. A
        // notch moves well under one card: this is a glance at a few hours, not a
        // journey through the day, and a fast strip is hard to stop on the hour you want.
        scroller.setWheelScrollingEnabled(false);
        scroller.addMouseWheelListener(event ->
                bar.setValue(bar.getValue() + event.getWheelRotation() * WHEEL_STEP));
        add(scroller, BorderLayout.CENTER);

        dayPlanViewModel.addPropertyChangeListener(dayPlanListener);
        refresh();
    }

    private void refreshSafely() {
        if (SwingUtilities.isEventDispatchThread()) {
            refresh();
        } else {
            SwingUtilities.invokeLater(this::refresh);
        }
    }

    private void refresh() {
        DayPlanState state = dayPlanViewModel.getState();
        List<WeatherWarning> hours = sortedWarnings(state.getHourlyWeather());

        cards.removeAll();
        if (hours.isEmpty()) {
            cards.add(updatingCard());
        } else {
            int firstDaytime = 0;
            for (int i = 0; i < hours.size(); i++) {
                if (i > 0) {
                    cards.add(Box.createHorizontalStrut(6));
                }
                WeatherWarning hour = hours.get(i);
                cards.add(hourCard(hour));
                if (firstDaytime == 0 && hour.getTime().equals(FIRST_INTERESTING_HOUR)) {
                    firstDaytime = i;
                }
            }
            final int scrollTo = firstDaytime * (CARD_WIDTH + 6);
            SwingUtilities.invokeLater(() ->
                    scroller.getHorizontalScrollBar().setValue(scrollTo));
        }
        cards.revalidate();
        cards.repaint();
    }

    private static List<WeatherWarning> sortedWarnings(List<WeatherWarning> hourlyWeather) {
        List<WeatherWarning> warnings = new ArrayList<>();
        if (hourlyWeather != null) {
            for (WeatherWarning warning : hourlyWeather) {
                if (warning != null && warning.getTime() != null) {
                    warnings.add(warning);
                }
            }
        }
        warnings.sort(Comparator.comparing(WeatherWarning::getTime));
        return warnings;
    }

    private JPanel hourCard(WeatherWarning hour) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setAlignmentY(Component.TOP_ALIGNMENT);
        Dimension size = new Dimension(CARD_WIDTH, CARD_HEIGHT);
        card.setPreferredSize(size);
        card.setMaximumSize(size);
        card.setMinimumSize(size);

        JLabel time = centered(HOUR_FORMAT.format(hour.getTime()), SwingTheme.SMALL);
        time.setForeground(SwingTheme.MUTED);

        JLabel glyph = centered(glyphFor(hour.getWeatherCondition()),
                new Font("SansSerif", Font.PLAIN, 22));
        glyph.setForeground(glyphColourFor(hour.getWeatherCondition()));

        JLabel temperature = centered(temperatureOf(hour), SwingTheme.BODY.deriveFont(Font.BOLD));
        temperature.setForeground(SwingTheme.NAVY);

        JLabel rain = centered(precipitationOf(hour), SwingTheme.SMALL);
        rain.setForeground(SwingTheme.BLUE);

        card.add(time);
        card.add(Box.createVerticalStrut(2));
        card.add(glyph);
        card.add(Box.createVerticalStrut(2));
        card.add(temperature);
        card.add(rain);

        String spoken = HOUR_FORMAT.format(hour.getTime()) + ", "
                + safe(hour.getWeatherCondition()) + ", " + temperatureOf(hour)
                + (precipitationOf(hour).isEmpty() ? "" : ", " + precipitationOf(hour)
                + " precipitation");
        card.getAccessibleContext().setAccessibleName(spoken);
        card.setToolTipText(spoken);
        return card;
    }

    private static JLabel centered(String text, Font font) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(font);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private JPanel updatingCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(false);
        JLabel message = new JLabel("Weather is updating… the hourly forecast will appear "
                + "here automatically.", SwingConstants.CENTER);
        message.setFont(SwingTheme.SMALL);
        message.setForeground(SwingTheme.MUTED);
        card.add(message, BorderLayout.CENTER);
        return card;
    }

    /**
     * A muted colour per condition — sun amber, rain blue, snow ice, storm orange.
     *
     * <p>Softened rather than saturated: eight of these sit in a row over a map, and full
     * strength would turn a quiet strip into bunting. Colour only ever repeats what the
     * glyph and the numbers already say, so nothing is lost if it cannot be seen.</p>
     */
    static Color glyphColourFor(String condition) {
        String lower = condition == null ? "" : condition.toLowerCase(Locale.ENGLISH);
        if (lower.contains("thunder") || lower.contains("storm")) {
            return new Color(214, 132, 42);
        }
        if (lower.contains("snow")) {
            return new Color(126, 178, 209);
        }
        if (lower.contains("rain") || lower.contains("drizzle")) {
            return new Color(74, 134, 196);
        }
        if (lower.contains("cloud") || lower.contains("overcast")) {
            return new Color(146, 158, 171);
        }
        if (lower.contains("fog")) {
            return new Color(165, 172, 180);
        }
        return new Color(233, 178, 47);
    }

    /**
     * A scrollbar that stays out of the way: a thin muted track with no arrow buttons.
     *
     * <p>The default Swing bar is as tall as a card is wide and draws more attention than
     * the forecast it scrolls. This one is a hairline that reads as an affordance without
     * competing with the hours.</p>
     */
    private static final class QuietScrollBarUI
            extends javax.swing.plaf.basic.BasicScrollBarUI {

        private static final Color THUMB = new Color(198, 205, 212);

        @Override
        protected void configureScrollBarColors() {
            trackColor = SwingTheme.PANEL;
        }

        @Override
        protected javax.swing.JButton createDecreaseButton(int orientation) {
            return zeroSizeButton();
        }

        @Override
        protected javax.swing.JButton createIncreaseButton(int orientation) {
            return zeroSizeButton();
        }

        private javax.swing.JButton zeroSizeButton() {
            javax.swing.JButton button = new javax.swing.JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintTrack(java.awt.Graphics g, javax.swing.JComponent c,
                                  java.awt.Rectangle bounds) {
            g.setColor(SwingTheme.PANEL);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        protected void paintThumb(java.awt.Graphics graphics, javax.swing.JComponent c,
                                  java.awt.Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(THUMB);
            int height = Math.min(bounds.height, 4);
            int y = bounds.y + (bounds.height - height) / 2;
            g.fillRoundRect(bounds.x + 2, y, bounds.width - 4, height, height, height);
            g.dispose();
        }
    }

    /** Same condition-to-glyph mapping the old dialog used, so nothing changes meaning. */
    static String glyphFor(String condition) {
        String lower = condition == null ? "" : condition.toLowerCase(Locale.ENGLISH);
        if (lower.contains("thunder") || lower.contains("storm")) {
            return "⚡";
        }
        if (lower.contains("snow")) {
            return "❄";
        }
        if (lower.contains("rain") || lower.contains("drizzle")) {
            return "☂";
        }
        if (lower.contains("cloud") || lower.contains("overcast")) {
            return "☁";
        }
        if (lower.contains("fog")) {
            return "≋";
        }
        return "☀";
    }

    /**
     * Pulls "24°C" out of a message like "24°C · 10% precipitation · low conditions.".
     *
     * <p>The message is display prose assembled by the weather side, not a data contract,
     * so this parses defensively and falls back to nothing rather than guessing.</p>
     */
    private static String temperatureOf(WeatherWarning hour) {
        for (String part : safe(hour.getMessage()).split("·")) {
            String token = part.trim();
            if (token.contains("°")) {
                return token;
            }
        }
        return "—";
    }

    private static String precipitationOf(WeatherWarning hour) {
        for (String part : safe(hour.getMessage()).split("·")) {
            String token = part.trim();
            if (token.toLowerCase(Locale.ENGLISH).contains("precipitation")) {
                return token.replaceAll("(?i)\\s*precipitation.*", "");
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public void disposeListeners() {
        if (!listening) {
            return;
        }
        listening = false;
        dayPlanViewModel.removePropertyChangeListener(dayPlanListener);
    }
}
