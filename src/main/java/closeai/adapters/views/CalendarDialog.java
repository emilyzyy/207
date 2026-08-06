package closeai.adapters.views;

import closeai.adapters.viewmodels.CalendarViewModel;
import closeai.adapters.viewmodels.DayPlanViewModel;
import java.awt.BorderLayout;
import java.awt.Frame;
import javax.swing.JDialog;

/** Modeless interactive calendar owned by the main application frame. */
public final class CalendarDialog extends JDialog {
    private final CalendarViewModel calendarViewModel;
    private final DayPlanViewModel dayPlanViewModel;

    public CalendarDialog(
            Frame owner,
            CalendarViewModel calendarViewModel,
            DayPlanViewModel dayPlanViewModel) {
        super(owner, "Calendar · Trip Planner", false);
        if (calendarViewModel == null || dayPlanViewModel == null) {
            throw new IllegalArgumentException("Calendar ViewModels are required");
        }
        this.calendarViewModel = calendarViewModel;
        this.dayPlanViewModel = dayPlanViewModel;
        setSize(980, 680);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        add(new CalendarPanel(calendarViewModel), BorderLayout.CENTER);
    }

    /** Retains the original composition contract used to verify shared schedule identity. */
    public DayPlanViewModel getViewModel() {
        return dayPlanViewModel;
    }

    public CalendarViewModel getCalendarViewModel() {
        return calendarViewModel;
    }
}
