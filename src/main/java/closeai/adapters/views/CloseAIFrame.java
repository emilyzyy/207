package closeai.adapters.views;

import closeai.adapters.viewmodels.DayPlanViewModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

/** Main Swing frame for the milestone dashboard. */
public final class CloseAIFrame extends JFrame {
    private final CalendarDialog calendarDialog;
    private final DayPlanPanel dayPlanPanel;

    public CloseAIFrame(
            HeaderPanel headerPanel,
            OverviewPanel overviewPanel,
            PlannerPanel plannerPanel,
            DayPlanPanel dayPlanPanel,
            DayPlanViewModel dayPlanViewModel) {
        super("CloseAI Trip Planner");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 680));
        setPreferredSize(new Dimension(1320, 820));

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(SwingTheme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));
        root.add(headerPanel, BorderLayout.NORTH);

        JSplitPane content = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, overviewPanel, plannerPanel);
        content.setResizeWeight(0.42);
        content.setDividerSize(8);
        content.setBorder(BorderFactory.createEmptyBorder());
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);

        this.dayPlanPanel = dayPlanPanel;
        calendarDialog = new CalendarDialog(this, dayPlanViewModel);
        dayPlanPanel.setOpenCalendarAction(() -> {
            calendarDialog.setLocationRelativeTo(this);
            calendarDialog.setVisible(true);
        });

        pack();
        setLocationRelativeTo(null);
    }

    public CalendarDialog getCalendarDialog() {
        return calendarDialog;
    }

    public DayPlanPanel getDayPlanPanel() {
        return dayPlanPanel;
    }
}
