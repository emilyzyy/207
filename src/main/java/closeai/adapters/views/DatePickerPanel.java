package closeai.adapters.views;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A month-at-a-glance date picker: the user navigates with previous/next arrows and
 * clicks a day to select it. The selected date is kept in the widget's own state.
 */
public final class DatePickerPanel extends JPanel {
    private static final DateTimeFormatter MONTH_HEADER = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final int GRID_ROWS = 6;
    private static final int GRID_COLUMNS = 7;

    private LocalDate selected;
    private YearMonth displayed;

    private final JLabel monthLabel = new JLabel("", JLabel.CENTER);
    private final JPanel grid = new JPanel(new GridLayout(GRID_ROWS + 1, GRID_COLUMNS, 4, 4));

    public DatePickerPanel() {
        this(LocalDate.now());
    }

    public DatePickerPanel(LocalDate initial) {
        selected = initial;
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

        render();
    }

    public LocalDate getDate() {
        return selected;
    }

    private void shiftMonth(int delta) {
        displayed = displayed.plusMonths(delta);
        render();
    }

    private void render() {
        monthLabel.setText(MONTH_HEADER.format(displayed.atDay(1)));
        grid.removeAll();
        for (DayOfWeek day : DayOfWeek.values()) {
            JLabel label = new JLabel(day.toString().substring(0, 3), JLabel.CENTER);
            label.setFont(SwingTheme.SMALL.deriveFont(Font.BOLD));
            label.setForeground(SwingTheme.MUTED);
            grid.add(label);
        }
        LocalDate gridStart = displayed.atDay(1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int index = 0; index < GRID_ROWS * GRID_COLUMNS; index++) {
            grid.add(dayButton(gridStart.plusDays(index)));
        }
        grid.revalidate();
        grid.repaint();
    }

    private JButton dayButton(LocalDate date) {
        JButton button = new JButton(String.valueOf(date.getDayOfMonth()));
        button.setFont(SwingTheme.SMALL);
        button.setFocusPainted(false);
        button.setOpaque(true);
        boolean inMonth = YearMonth.from(date).equals(displayed);
        boolean isSelected = date.equals(selected);
        button.setForeground(inMonth ? SwingTheme.NAVY : SwingTheme.MUTED);
        button.setBackground(isSelected ? SwingTheme.BLUE_SOFT : SwingTheme.PANEL);
        button.setBorder(isSelected
                ? BorderFactory.createLineBorder(SwingTheme.BLUE, 2)
                : BorderFactory.createLineBorder(SwingTheme.LINE));
        button.setEnabled(inMonth);
        button.addActionListener(e -> {
            selected = date;
            render();
        });
        button.getAccessibleContext().setAccessibleName("date " + date);
        return button;
    }
}
