package views;

import interface_adapter.viewmodels.CalendarState;
import interface_adapter.viewmodels.CalendarViewMode;
import interface_adapter.viewmodels.CalendarViewModel;
import entity.entities.ScheduledEvent;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Interactive Day/Week/Month calendar backed by the active trip's shared state. */
public final class CalendarPanel extends JPanel {
    private static final DateTimeFormatter DAY_TITLE =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_TITLE =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY =
            DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final CalendarViewModel viewModel;
    private final JLabel title = new JLabel();
    private final JLabel context = new JLabel();
    private final JPanel content = new JPanel(new BorderLayout());
    private final JComboBox<CalendarViewMode> viewMode =
            new JComboBox<CalendarViewMode>(CalendarViewMode.values());
    private final JButton tripDateButton = new JButton("Trip date");
    private boolean rendering;

    public CalendarPanel(CalendarViewModel viewModel) {
        if (viewModel == null) {
            throw new IllegalArgumentException("Calendar ViewModel is required");
        }
        this.viewModel = viewModel;
        SwingTheme.styleComboBox(viewMode);
        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        add(toolbar(), BorderLayout.NORTH);
        content.setBackground(SwingTheme.BACKGROUND);
        add(content, BorderLayout.CENTER);
        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> render(viewModel.getState()));
    }

    public CalendarViewModel getViewModel() {
        return viewModel;
    }

    private JPanel toolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(12, 10));
        toolbar.setOpaque(false);

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        title.setName("calendar-title");
        title.setFont(SwingTheme.TITLE);
        title.setForeground(SwingTheme.NAVY);
        context.setFont(SwingTheme.SMALL);
        context.setForeground(SwingTheme.MUTED);
        heading.add(title);
        heading.add(Box.createVerticalStrut(3));
        heading.add(context);
        toolbar.add(heading, BorderLayout.CENTER);

        JPanel navigation = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        navigation.setOpaque(false);
        JButton previous = new JButton("‹");
        previous.setName("calendar-previous");
        previous.setToolTipText("Previous period");
        previous.addActionListener(event -> viewModel.previousPeriod());
        navigation.add(previous);
        JButton today = new JButton("Today");
        today.setName("calendar-today");
        today.addActionListener(event -> viewModel.goToToday());
        navigation.add(today);
        tripDateButton.setName("calendar-trip-date");
        tripDateButton.addActionListener(event -> viewModel.goToTripDate());
        navigation.add(tripDateButton);
        JButton next = new JButton("›");
        next.setName("calendar-next");
        next.setToolTipText("Next period");
        next.addActionListener(event -> viewModel.nextPeriod());
        navigation.add(next);
        viewMode.setName("calendar-view-mode");
        viewMode.setFont(SwingTheme.BODY);
        viewMode.addActionListener(event -> {
            if (!rendering) {
                viewModel.setViewMode((CalendarViewMode) viewMode.getSelectedItem());
            }
        });
        navigation.add(viewMode);
        toolbar.add(navigation, BorderLayout.EAST);
        return toolbar;
    }

    private void render(CalendarState state) {
        rendering = true;
        viewMode.setSelectedItem(state.getViewMode());
        rendering = false;
        tripDateButton.setEnabled(state.getTripDate() != null);
        title.setText(titleFor(state));
        context.setText(contextFor(state));
        content.removeAll();
        switch (state.getViewMode()) {
            case DAY:
                content.add(dayView(state), BorderLayout.CENTER);
                break;
            case WEEK:
                content.add(weekView(state), BorderLayout.CENTER);
                break;
            case MONTH:
                content.add(monthView(state), BorderLayout.CENTER);
                break;
            default:
                throw new IllegalStateException("Unsupported calendar view");
        }
        content.revalidate();
        content.repaint();
    }

    private String titleFor(CalendarState state) {
        if (state.getViewMode() == CalendarViewMode.DAY) {
            return DAY_TITLE.format(state.getFocusDate());
        }
        if (state.getViewMode() == CalendarViewMode.WEEK) {
            LocalDate start = weekStart(state.getFocusDate());
            LocalDate end = start.plusDays(6);
            return start.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
                    + " – " + end.format(DateTimeFormatter.ofPattern(
                            "MMM d, yyyy", Locale.ENGLISH));
        }
        return MONTH_TITLE.format(state.getFocusDate());
    }

    private String contextFor(CalendarState state) {
        if (state.getTripDate() == null) {
            return "Create a trip to place it on the calendar.";
        }
        String destination = state.getDestination().isEmpty()
                ? "Active trip" : state.getDestination();
        return destination + " · " + dateRange(state.getTripDates())
                + " · Day " + (state.getActiveDayIndex() + 1) + " of "
                + state.getTripDates().size()
                + " · " + state.getEvents().size() + " scheduled item(s)";
    }

    private String dateRange(List<LocalDate> dates) {
        if (dates.isEmpty()) {
            return "";
        }
        if (dates.size() == 1) {
            return DAY_TITLE.format(dates.get(0));
        }
        DateTimeFormatter shortMonth =
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
        return dates.get(0).format(shortMonth) + " – "
                + dates.get(dates.size() - 1).format(shortMonth);
    }

    private JPanel dayView(CalendarState state) {
        JPanel timeline = verticalPanel();
        timeline.setBorder(BorderFactory.createEmptyBorder(4, 2, 12, 2));
        if (!state.isTripDate(state.getFocusDate())) {
            timeline.add(emptyLabel("No Trippy trip is scheduled for this date."));
        } else if (state.getEvents().isEmpty()) {
            timeline.add(emptyLabel("This trip has no scheduled activities yet."));
        } else {
            for (ScheduledEvent event : state.getEvents()) {
                timeline.add(eventCard(event));
                timeline.add(Box.createVerticalStrut(9));
            }
        }
        JScrollPane scroll = new JScrollPane(timeline);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel weekView(CalendarState state) {
        JPanel week = new JPanel(new GridLayout(1, 7, 7, 0));
        week.setOpaque(false);
        LocalDate start = weekStart(state.getFocusDate());
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = start.plusDays(offset);
            JPanel day = verticalPanel();
            day.setBackground(state.isTripDate(date)
                    ? SwingTheme.BLUE_SOFT : SwingTheme.PANEL);
            day.setBorder(date.equals(state.getFocusDate())
                    ? BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(SwingTheme.BLUE, 2),
                            BorderFactory.createEmptyBorder(9, 9, 9, 9))
                    : state.isActiveTripDate(date)
                    ? BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(SwingTheme.BLUE, 2),
                            BorderFactory.createEmptyBorder(9, 9, 9, 9))
                    : state.isTripDate(date)
                    ? BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(SwingTheme.BLUE),
                            BorderFactory.createEmptyBorder(10, 10, 10, 10))
                    : SwingTheme.cardBorder());
            JButton dayButton = new JButton(SHORT_DAY.format(date));
            dayButton.setName("calendar-day-" + date);
            dayButton.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
            dayButton.addActionListener(event -> viewModel.selectDate(date));
            day.add(dayButton);
            day.add(Box.createVerticalStrut(8));
            if (state.isTripDate(date)) {
                JLabel destination = new JLabel(dayHeading(state, date));
                destination.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
                destination.setForeground(SwingTheme.BLUE);
                day.add(destination);
                if (state.isActiveTripDate(date)) {
                    for (ScheduledEvent event : state.getEvents()) {
                        day.add(Box.createVerticalStrut(6));
                        JLabel eventLabel = new JLabel("<html>" + TIME.format(event.getStartTime())
                                + "<br><b>" + escapeHtml(eventName(event)) + "</b></html>");
                        eventLabel.setFont(SwingTheme.SMALL);
                        eventLabel.setForeground(SwingTheme.NAVY);
                        day.add(eventLabel);
                    }
                } else {
                    day.add(Box.createVerticalStrut(4));
                    day.add(emptyLabel("Other trip day"));
                }
            } else {
                day.add(emptyLabel("No trip"));
            }
            week.add(day);
        }
        return week;
    }

    private String dayHeading(CalendarState state, LocalDate date) {
        int dayIndex = state.getTripDates().indexOf(date);
        String label = "Day " + (dayIndex + 1);
        if (state.isActiveTripDate(date)) {
            label = "● " + label;
        }
        if (!state.getDestination().isEmpty()) {
            label = label + " · " + state.getDestination();
        }
        return label;
    }

    private JPanel monthView(CalendarState state) {
        JPanel month = new JPanel(new GridLayout(7, 7, 6, 6));
        month.setOpaque(false);
        for (DayOfWeek day : DayOfWeek.values()) {
            JLabel label = new JLabel(day.toString().substring(0, 3), JLabel.CENTER);
            label.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
            label.setForeground(SwingTheme.MUTED);
            month.add(label);
        }
        YearMonth displayedMonth = YearMonth.from(state.getFocusDate());
        LocalDate gridDate = displayedMonth.atDay(1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int index = 0; index < 42; index++) {
            LocalDate date = gridDate.plusDays(index);
            JButton day = new JButton(monthCellText(state, date));
            day.setName("calendar-day-" + date);
            day.setToolTipText(DAY_TITLE.format(date));
            day.setFont(SwingTheme.SMALL);
            day.setHorizontalAlignment(JButton.LEFT);
            day.setVerticalAlignment(JButton.TOP);
            day.setOpaque(true);
            day.setBackground(state.isTripDate(date) ? SwingTheme.BLUE_SOFT : SwingTheme.PANEL);
            day.setForeground(displayedMonth.equals(YearMonth.from(date))
                    ? SwingTheme.NAVY : SwingTheme.MUTED);
            day.setBorder(date.equals(state.getFocusDate())
                    ? BorderFactory.createLineBorder(SwingTheme.BLUE, 2)
                    : state.isActiveTripDate(date)
                    ? BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(SwingTheme.BLUE),
                            BorderFactory.createEmptyBorder(3, 3, 3, 3))
                    : BorderFactory.createLineBorder(SwingTheme.LINE));
            day.addActionListener(event -> viewModel.selectDate(date));
            month.add(day);
        }
        return month;
    }

    private String monthCellText(CalendarState state, LocalDate date) {
        StringBuilder text = new StringBuilder("<html><b>")
                .append(date.getDayOfMonth()).append("</b>");
        if (state.isTripDate(date)) {
            int dayIndex = state.getTripDates().indexOf(date);
            String marker = state.isActiveTripDate(date) ? "● " : "";
            text.append("<br><font color='#1f68e1'>").append(marker)
                    .append(escapeHtml(state.getDestination().isEmpty()
                            ? "Trip" : state.getDestination()))
                    .append(" · D").append(dayIndex + 1)
                    .append("</font>");
            if (state.isActiveTripDate(date) && !state.getEvents().isEmpty()) {
                text.append("<br>").append(state.getEvents().size()).append(" item(s)");
            }
        }
        return text.append("</html>").toString();
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private JPanel eventCard(ScheduledEvent event) {
        JPanel card = new JPanel(new BorderLayout(14, 4));
        SwingTheme.styleCard(card);
        if (event.getActivity() != null) {
            card.setBackground(SwingTheme.categorySurface(
                    event.getActivity().getCategory()));
        }
        JLabel time = new JLabel(TIME.format(event.getStartTime())
                + " – " + TIME.format(event.getEndTime()));
        time.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        time.setForeground(SwingTheme.BLUE);
        card.add(time, BorderLayout.WEST);
        JLabel name = new JLabel(eventName(event));
        name.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        name.setForeground(SwingTheme.NAVY);
        card.add(name, BorderLayout.CENTER);
        return card;
    }

    private String eventName(ScheduledEvent event) {
        if (event.getActivity() != null) {
            return event.getActivity().getName();
        }
        return event.getNotes().trim().isEmpty()
                ? event.getEventType().toString() : event.getNotes();
    }

    private JLabel emptyLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SwingTheme.SMALL);
        label.setForeground(SwingTheme.MUTED);
        return label;
    }

    private JPanel verticalPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SwingTheme.PANEL);
        return panel;
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
