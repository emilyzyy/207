package closeai.adapters.views;

import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.WeatherSeverity;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** Scrollable full-day forecast content shared by the modeless weather dialog and tests. */
public final class HourlyWeatherPanel extends JPanel {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final DashboardViewModel dashboardViewModel;
    private final DayPlanViewModel dayPlanViewModel;
    private final JLabel titleLabel = new JLabel("Hourly forecast");
    private final JLabel subtitleLabel = new JLabel();
    private final JLabel countLabel = new JLabel();
    private final JPanel forecastList = new JPanel();
    private final PropertyChangeListener dashboardListener = event -> refreshSafely();
    private final PropertyChangeListener dayPlanListener = event -> refreshSafely();
    private boolean listening = true;

    public HourlyWeatherPanel(
            DashboardViewModel dashboardViewModel,
            DayPlanViewModel dayPlanViewModel) {
        if (dashboardViewModel == null || dayPlanViewModel == null) {
            throw new IllegalArgumentException("Weather ViewModels are required");
        }
        this.dashboardViewModel = dashboardViewModel;
        this.dayPlanViewModel = dayPlanViewModel;

        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel heading = new JPanel(new BorderLayout(12, 4));
        heading.setOpaque(false);
        JPanel copy = new JPanel(new GridLayout(0, 1, 0, 3));
        copy.setOpaque(false);
        titleLabel.setFont(SwingTheme.TITLE);
        titleLabel.setForeground(SwingTheme.NAVY);
        subtitleLabel.setFont(SwingTheme.BODY);
        subtitleLabel.setForeground(SwingTheme.MUTED);
        copy.add(titleLabel);
        copy.add(subtitleLabel);
        heading.add(copy, BorderLayout.CENTER);
        countLabel.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
        countLabel.setForeground(SwingTheme.BLUE);
        countLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        heading.add(countLabel, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        forecastList.setLayout(new BoxLayout(forecastList, BoxLayout.Y_AXIS));
        forecastList.setBackground(SwingTheme.BACKGROUND);
        JScrollPane scrollPane = new JScrollPane(forecastList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(SwingTheme.BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        dashboardViewModel.addPropertyChangeListener(dashboardListener);
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
        DashboardState dashboard = dashboardViewModel.getState();
        DayPlanState dayPlan = dayPlanViewModel.getState();
        LocalDate date = dashboard.getDate();
        String destination = dashboard.getDestination().isEmpty()
                ? "No destination selected" : dashboard.getDestination();
        subtitleLabel.setText(destination + (date == null ? "" : " · " + DATE_FORMAT.format(date)));

        List<WeatherWarning> warnings = sortedWarnings(dayPlan.getHourlyWeather());
        countLabel.setText(warnings.isEmpty()
                ? "UPDATING" : warnings.size() + " HOURLY FORECASTS");
        forecastList.removeAll();
        if (warnings.isEmpty()) {
            forecastList.add(emptyState());
        } else {
            for (WeatherWarning warning : warnings) {
                forecastList.add(forecastRow(warning));
                forecastList.add(javax.swing.Box.createVerticalStrut(8));
            }
        }
        forecastList.revalidate();
        forecastList.repaint();
    }

    private List<WeatherWarning> sortedWarnings(List<WeatherWarning> hourlyWeather) {
        List<WeatherWarning> warnings = new ArrayList<WeatherWarning>();
        if (hourlyWeather != null) {
            for (WeatherWarning warning : hourlyWeather) {
                if (warning != null && warning.getTime() != null) warnings.add(warning);
            }
        }
        warnings.sort(Comparator.comparing(WeatherWarning::getTime));
        return warnings;
    }

    private JPanel emptyState() {
        JPanel empty = new JPanel(new BorderLayout());
        SwingTheme.styleCard(empty);
        empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        JLabel message = new JLabel(
                "<html><b>Weather is updating…</b><br>"
                        + "The full-day forecast will appear here automatically.</html>");
        message.setFont(SwingTheme.BODY);
        message.setForeground(SwingTheme.MUTED);
        empty.add(message, BorderLayout.CENTER);
        return empty;
    }

    private JPanel forecastRow(WeatherWarning warning) {
        JPanel row = new JPanel(new BorderLayout(14, 4));
        SwingTheme.styleCard(row);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));

        JLabel time = new JLabel(TIME_FORMAT.format(warning.getTime()));
        time.setPreferredSize(new Dimension(78, 40));
        time.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        time.setForeground(SwingTheme.NAVY);
        row.add(time, BorderLayout.WEST);

        JPanel copy = new JPanel(new GridLayout(0, 1, 0, 3));
        copy.setOpaque(false);
        JLabel condition = new JLabel(weatherIcon(warning) + "  " + safe(warning.getWeatherCondition()));
        condition.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        condition.setForeground(SwingTheme.NAVY);
        JLabel details = new JLabel(safe(warning.getMessage()));
        details.setFont(SwingTheme.SMALL);
        details.setForeground(SwingTheme.MUTED);
        copy.add(condition);
        copy.add(details);
        row.add(copy, BorderLayout.CENTER);

        JLabel severity = new JLabel(severityText(warning.getSeverity()));
        severity.setOpaque(true);
        severity.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
        severity.setForeground(severityColor(warning.getSeverity()));
        severity.setBackground(severityBackground(warning.getSeverity()));
        severity.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        row.add(severity, BorderLayout.EAST);
        return row;
    }

    private String weatherIcon(WeatherWarning warning) {
        String condition = safe(warning.getWeatherCondition()).toLowerCase(Locale.ENGLISH);
        if (condition.contains("thunder") || condition.contains("storm")) return "⚡";
        if (condition.contains("snow")) return "❄";
        if (condition.contains("rain") || condition.contains("drizzle")) return "☂";
        if (condition.contains("cloud") || condition.contains("overcast")) return "☁";
        if (condition.contains("fog")) return "≋";
        return "☀";
    }

    private String severityText(WeatherSeverity severity) {
        return severity == null ? "UNKNOWN" : severity.name();
    }

    private Color severityColor(WeatherSeverity severity) {
        if (severity == WeatherSeverity.HIGH) return SwingTheme.ERROR;
        if (severity == WeatherSeverity.MEDIUM) return new Color(145, 93, 0);
        return SwingTheme.SUCCESS;
    }

    private Color severityBackground(WeatherSeverity severity) {
        if (severity == WeatherSeverity.HIGH) return new Color(255, 236, 234);
        if (severity == WeatherSeverity.MEDIUM) return new Color(255, 247, 222);
        return new Color(232, 248, 239);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public void disposeListeners() {
        if (!listening) return;
        listening = false;
        dashboardViewModel.removePropertyChangeListener(dashboardListener);
        dayPlanViewModel.removePropertyChangeListener(dayPlanListener);
    }
}
