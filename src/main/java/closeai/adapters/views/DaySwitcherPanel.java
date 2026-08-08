package closeai.adapters.views;

import closeai.adapters.controllers.TripDayController;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * A strip of one toggle per trip day, always visible above the planner tabs so the active
 * day can be switched from Search, Bookmarks, Day Plan, or Trip Options. Hidden for
 * single-day trips, where there is nothing to switch between.
 */
public final class DaySwitcherPanel extends JPanel {
    private final DayPlanViewModel viewModel;
    private final TripDayController tripDayController;
    private final JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

    public DaySwitcherPanel(DayPlanViewModel viewModel, TripDayController tripDayController) {
        this.viewModel = viewModel;
        this.tripDayController = tripDayController;
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 16, 0, 16));
        strip.setOpaque(false);
        add(strip, BorderLayout.CENTER);

        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event ->
                onEventThread(() -> render(viewModel.getState())));
    }

    private void render(DayPlanState state) {
        strip.removeAll();
        List<LocalDate> dates = state.getTripDates();
        if (dates.size() <= 1 || tripDayController == null) {
            setVisible(false);
            return;
        }
        setVisible(true);
        for (int i = 0; i < dates.size(); i++) {
            final int index = i;
            boolean active = index == state.getActiveDayIndex();
            JToggleButton day = new JToggleButton((active ? "\u25cf " : "") + "Day "
                    + (i + 1) + " \u00b7 " + dates.get(i), active);
            day.setFont(SwingTheme.SMALL);
            day.setFocusPainted(true);
            day.setOpaque(true);
            day.setBackground(active ? SwingTheme.BLUE : SwingTheme.BACKGROUND);
            day.setForeground(active ? Color.WHITE : SwingTheme.MUTED);
            day.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(active ? SwingTheme.BLUE : SwingTheme.LINE),
                    BorderFactory.createEmptyBorder(4, 10, 4, 10)));
            day.setToolTipText("Show " + dates.get(i));
            day.addActionListener(event -> tripDayController.switchTo(index));
            strip.add(day);
        }
        revalidate();
        repaint();
    }

    private static void onEventThread(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
