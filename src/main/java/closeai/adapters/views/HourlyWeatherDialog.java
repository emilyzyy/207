package closeai.adapters.views;

import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.DayPlanViewModel;
import java.awt.BorderLayout;
import java.awt.Frame;
import javax.swing.JDialog;

/** Modeless forecast window that stays synchronized with the active trip's weather state. */
public final class HourlyWeatherDialog extends JDialog {
    private final HourlyWeatherPanel forecastPanel;

    public HourlyWeatherDialog(
            Frame owner,
            DashboardViewModel dashboardViewModel,
            DayPlanViewModel dayPlanViewModel) {
        super(owner, "Hourly Weather · Trip Planner", false);
        if (dashboardViewModel == null || dayPlanViewModel == null) {
            throw new IllegalArgumentException("Weather ViewModels are required");
        }
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(640, 520);
        setMinimumSize(new java.awt.Dimension(520, 380));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        forecastPanel = new HourlyWeatherPanel(dashboardViewModel, dayPlanViewModel);
        add(forecastPanel, BorderLayout.CENTER);
    }

    @Override
    public void dispose() {
        forecastPanel.disposeListeners();
        super.dispose();
    }
}
