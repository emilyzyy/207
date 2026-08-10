package views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import interface_adapter.controllers.TripDayController;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;

/**
 * A single-row strip of one toggle per trip day, above the planner tabs so the active day can be
 * switched from Search, Bookmarks, Day Plan, or Trip Options. The strip scrolls horizontally when
 * a trip spans more days than fit, instead of growing to fill the screen. Hidden for single-day
 * trips, where there is nothing to switch between.
 */
public final class DaySwitcherPanel extends JPanel {
    private final DayPlanViewModel viewModel;
    private final TripDayController tripDayController;
    private final JPanel strip = new JPanel();
    private final JScrollPane scroller;

    public DaySwitcherPanel(DayPlanViewModel viewModel, TripDayController tripDayController) {
        this.viewModel = viewModel;
        this.tripDayController = tripDayController;
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 16, 0, 16));

        strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));
        strip.setOpaque(false);
        scroller = new JScrollPane(strip,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroller.setBorder(null);
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        scroller.getHorizontalScrollBar().setUnitIncrement(16);
        scroller.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                reserveScrollbarHeight();
            }
        });
        add(scroller, BorderLayout.CENTER);

        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event ->
                onEventThread(() -> render(viewModel.getState())));
    }

    private void render(DayPlanState state) {
        strip.removeAll();
        final List<LocalDate> dates = state.getTripDates();
        if (dates.size() <= 1 || tripDayController == null) {
            setVisible(false);
            return;
        }
        setVisible(true);
        JToggleButton activeDay = null;
        for (int i = 0; i < dates.size(); i++) {
            final int index = i;
            final boolean active = index == state.getActiveDayIndex();
            final JToggleButton day = new JToggleButton((active ? "\u25cf " : "") + "Day "
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
            if (i > 0) {
                strip.add(Box.createHorizontalStrut(8));
            }
            strip.add(day);
            if (active) {
                activeDay = day;
            }
        }
        strip.revalidate();
        strip.repaint();
        reserveScrollbarHeight();
        // Keep the selected day visible: switching days elsewhere (calendar, autoschedule)
        // must scroll a long strip to the active tab.
        if (activeDay != null) {
            final JToggleButton visible = activeDay;
            SwingUtilities.invokeLater(() -> strip.scrollRectToVisible(visible.getBounds()));
        }
    }

    /**
     * The scroll pane sizes its preferred height as if no scrollbar is needed, so a long trip
     * that shows the horizontal scrollbar would shrink the viewport and cover the tab bottoms.
     * Reserve the scrollbar's height only while the strip actually overflows the viewport.
     */
    private void reserveScrollbarHeight() {
        final int viewportWidth = scroller.getViewport().getExtentSize().width;
        final boolean overflows = viewportWidth > 0
                && strip.getPreferredSize().width > viewportWidth;
        final int target = strip.getPreferredSize().height
                + (overflows ? scroller.getHorizontalScrollBar().getPreferredSize().height : 0);
        final Dimension current = scroller.getPreferredSize();
        if (current == null || current.height != target) {
            scroller.setPreferredSize(new Dimension(
                    current == null ? 0 : current.width, target));
            revalidate();
        }
    }

    private static void onEventThread(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        }
        else {
            SwingUtilities.invokeLater(action);
        }
    }
}
