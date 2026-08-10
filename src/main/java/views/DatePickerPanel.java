package views;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * A month-at-a-glance date range picker. Click the first day, then drag over the
 * remaining days to select the range.
 *
 * <p>The 42-cell grid keeps the adjacent month's leading and trailing days enabled, so a
 * selection can span a month boundary mid-drag instead of stopping at the month's last
 * visible cell. A plain second click after the first (no drag) also extends the range. The
 * selectable range is capped at two weeks ({@value #MAX_RANGE_DAYS} days).</p>
 */
public final class DatePickerPanel extends JPanel {
    private static final DateTimeFormatter MONTH_HEADER = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final int GRID_ROWS = 6;
    private static final int GRID_COLUMNS = 7;
    private static final int MAX_RANGE_DAYS = 14;

    private LocalDate start;
    private LocalDate end;
    private YearMonth displayed;
    private LocalDate gridStart;
    private Runnable rangeChangeListener;
    private boolean allowClickExtend = true;
    private boolean dragging;
    private boolean dragged;
    private boolean pendingEnd;

    private final JLabel monthLabel = new JLabel("", JLabel.CENTER);
    private final JPanel grid = new JPanel(new GridLayout(GRID_ROWS + 1, GRID_COLUMNS, 4, 4));
    private final JButton[] dayButtons = new JButton[GRID_ROWS * GRID_COLUMNS];

    public DatePickerPanel() {
        this(LocalDate.now());
    }

    public DatePickerPanel(LocalDate initial) {
        start = initial;
        end = initial;
        displayed = YearMonth.from(initial);
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SwingTheme.LINE),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JButton previous = SwingTheme.secondaryButton("<");
        previous.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        previous.addActionListener(e -> shiftMonth(-1));
        JButton next = SwingTheme.secondaryButton(">");
        next.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        next.addActionListener(e -> shiftMonth(1));

        monthLabel.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
        monthLabel.setForeground(SwingTheme.NAVY);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(previous, BorderLayout.WEST);
        header.add(monthLabel, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);

        grid.setOpaque(false);

        setLayout(new BorderLayout(0, 8));
        add(header, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);

        buildGrid();
        render();
    }

    public LocalDate getDate() {
        return start != null ? start : LocalDate.now();
    }

    /** The earliest selected day. */
    public LocalDate getStartDate() {
        return start;
    }

    /** The latest selected day. */
    public LocalDate getEndDate() {
        return end;
    }

    /** Number of days covered by the selection, always at least 1. */
    public int getDayCount() {
        if (start == null || end == null) {
            return 1;
        }
        long days = Math.abs(ChronoUnit.DAYS.between(start, end)) + 1;
        return (int) Math.min(days, Integer.MAX_VALUE);
    }

    /** Invoked after the user finishes a selection gesture (drag or click). */
    public void setRangeChangeListener(Runnable listener) {
        this.rangeChangeListener = listener;
    }

    /** Programmatically replaces the selected range; does not notify the listener. */
    public void setRange(LocalDate newStart, LocalDate newEnd) {
        if (newStart == null) {
            return;
        }
        start = newStart;
        end = (newEnd != null && !newEnd.isBefore(newStart)) ? newEnd : newStart;
        if (end != null && ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            end = start.plusDays(MAX_RANGE_DAYS - 1);
        }
        displayed = YearMonth.from(start);
        render();
    }

    /**
     * Single-date pickers (which only read {@link #getDate()}) call this with {@code false}
     * so every click re-picks the day instead of extending a range on the second click.
     */
    public void setAllowClickExtend(boolean allow) {
        this.allowClickExtend = allow;
    }

    private void shiftMonth(int delta) {
        displayed = displayed.plusMonths(delta);
        render();
    }

    /** Builds the weekday header and the 42 day cells once; only re-styled afterwards. */
    private void buildGrid() {
        for (DayOfWeek day : DayOfWeek.values()) {
            JLabel label = new JLabel(day.toString().substring(0, 3), JLabel.CENTER);
            label.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
            label.setForeground(SwingTheme.MUTED);
            grid.add(label);
        }
        for (int index = 0; index < dayButtons.length; index++) {
            JButton button = new JButton();
            button.setFont(SwingTheme.SMALL);
            button.setFocusPainted(false);
            button.setOpaque(true);
            button.setBackground(SwingTheme.PANEL);
            button.setBorder(BorderFactory.createLineBorder(SwingTheme.LINE));
            button.addMouseListener(mouseHandler);
            button.addMouseMotionListener(mouseHandler);
            dayButtons[index] = button;
            grid.add(button);
        }
    }

    private void render() {
        monthLabel.setText(MONTH_HEADER.format(displayed.atDay(1)));
        gridStart = displayed.atDay(1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int index = 0; index < dayButtons.length; index++) {
            LocalDate date = gridStart.plusDays(index);
            JButton button = dayButtons[index];
            button.setText(String.valueOf(date.getDayOfMonth()));
            button.setForeground(YearMonth.from(date).equals(displayed)
                    ? SwingTheme.NAVY : SwingTheme.MUTED);
            button.getAccessibleContext().setAccessibleName("date " + date);
            applyRangeStyle(button, date);
        }
        grid.repaint();
    }

    private void applyRangeStyle(JButton button, LocalDate date) {
        boolean inRange = inRange(date);
        boolean endpoint = start != null && end != null
                && !start.equals(end)
                && (date.equals(start) || date.equals(end));
        if (inRange) {
            button.setBackground(SwingTheme.BLUE_SOFT);
            button.setBorder(endpoint
                    ? BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(SwingTheme.BLUE, 2),
                            BorderFactory.createEmptyBorder(3, 3, 3, 3))
                    : BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(SwingTheme.BLUE),
                            BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        } else {
            button.setBackground(SwingTheme.PANEL);
            button.setBorder(BorderFactory.createLineBorder(SwingTheme.LINE));
        }
    }

    private boolean inRange(LocalDate date) {
        if (start == null || end == null) {
            return false;
        }
        LocalDate lo = start.isBefore(end) ? start : end;
        LocalDate hi = start.isBefore(end) ? end : start;
        return !date.isBefore(lo) && !date.isAfter(hi);
    }

    private final MouseAdapter mouseHandler = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            LocalDate day = dayAt(e);
            if (day == null) return;
            if (allowClickExtend && pendingEnd) {
                end = clampToMaxRange(day);
                pendingEnd = false;
            } else {
                start = day;
                end = day;
            }
            dragging = true;
            dragged = false;
            render();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!dragging) return;
            LocalDate day = dayAt(e);
            if (day == null || day.equals(end)) return;
            end = clampToMaxRange(day);
            dragged = true;
            render();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!dragging) return;
            dragging = false;
            pendingEnd = !dragged;
            if (start != null && end != null && start.isAfter(end)) {
                LocalDate swap = start;
                start = end;
                end = swap;
            }
            render();
            if (rangeChangeListener != null) {
                rangeChangeListener.run();
            }
        }
    };

    /** Caps a candidate end day so the selected range never exceeds {@link #MAX_RANGE_DAYS}. */
    private LocalDate clampToMaxRange(LocalDate day) {
        if (start == null || day == null) {
            return day;
        }
        long span = ChronoUnit.DAYS.between(start, day);
        if (span >= MAX_RANGE_DAYS) {
            return start.plusDays(MAX_RANGE_DAYS - 1);
        }
        if (span <= -MAX_RANGE_DAYS) {
            return start.minusDays(MAX_RANGE_DAYS - 1);
        }
        return day;
    }

    /** Maps a mouse position (from any day cell) to the LocalDate under the cursor. */
    private LocalDate dayAt(MouseEvent e) {
        Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), grid);
        int w = grid.getWidth();
        int h = grid.getHeight();
        if (w <= 0 || h <= 0 || p.x < 0 || p.y < 0 || p.x >= w || p.y >= h) {
            return null;
        }
        int col = Math.min(GRID_COLUMNS - 1, p.x * GRID_COLUMNS / w);
        int row = Math.min(GRID_ROWS, p.y * (GRID_ROWS + 1) / h);
        if (row == 0) {
            return null;
        }
        return gridStart.plusDays((row - 1) * GRID_COLUMNS + col);
    }
}
