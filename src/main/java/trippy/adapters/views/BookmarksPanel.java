package trippy.adapters.views;

import trippy.adapters.controllers.BookmarkController;
import trippy.adapters.controllers.ManualPlanController;
import trippy.adapters.viewmodels.ActivitySelectionViewModel;
import trippy.adapters.viewmodels.BookmarksState;
import trippy.adapters.viewmodels.BookmarksViewModel;
import trippy.adapters.viewmodels.DayPlanViewModel;
import trippy.adapters.viewmodels.TripOptionsViewModel;
import trippy.domain.entities.Activity;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Saved-activity view synchronized with the active trip's bookmarks. */
public final class BookmarksPanel extends JPanel {
    private final BookmarksViewModel viewModel;
    private final BookmarkController controller;
    private final ManualPlanController manualPlan;
    private final ActivitySelectionViewModel selection;
    private final DayPlanViewModel dayPlan;
    private final TripOptionsViewModel tripOptions;
    private final JPanel list = new JPanel();

    public BookmarksPanel(BookmarksViewModel viewModel) {
        this(viewModel, null, null, null, null, null);
    }

    public BookmarksPanel(BookmarksViewModel viewModel, BookmarkController controller) {
        this(viewModel, controller, null, null, null, null);
    }

    public BookmarksPanel(BookmarksViewModel viewModel, BookmarkController controller,
                          ManualPlanController manualPlan) {
        this(viewModel, controller, manualPlan, null, null, null);
    }

    public BookmarksPanel(BookmarksViewModel viewModel, BookmarkController controller,
                          ManualPlanController manualPlan,
                          ActivitySelectionViewModel selection) {
        this(viewModel, controller, manualPlan, selection, null, null);
    }

    public BookmarksPanel(BookmarksViewModel viewModel, BookmarkController controller,
                          ManualPlanController manualPlan,
                          ActivitySelectionViewModel selection,
                          DayPlanViewModel dayPlan,
                          TripOptionsViewModel tripOptions) {
        this.viewModel = viewModel;
        this.controller = controller;
        this.manualPlan = manualPlan;
        this.selection = selection;
        this.dayPlan = dayPlan;
        this.tripOptions = tripOptions;
        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Saved for later");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        heading.add(title);
        JLabel copy = new JLabel("Bookmark activities while exploring, then use them when planning.");
        copy.setFont(SwingTheme.SMALL);
        copy.setForeground(SwingTheme.MUTED);
        heading.add(copy);
        add(heading, BorderLayout.NORTH);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(SwingTheme.PANEL);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> render(viewModel.getState()));
        if (selection != null) {
            selection.addPropertyChangeListener(event -> render(viewModel.getState()));
        }
    }

    private void render(BookmarksState state) {
        list.removeAll();
        if (state.getBookmarks().isEmpty()) {
            JLabel empty = new JLabel("No saved activities yet");
            empty.setFont(SwingTheme.BODY);
            empty.setForeground(SwingTheme.MUTED);
            list.add(empty);
        }
        for (Activity activity : state.getBookmarks()) {
            JPanel card = new JPanel(new BorderLayout());
            SwingTheme.styleCard(card);
            card.setBackground(SwingTheme.categorySurface(activity.getCategory()));
            makeSelectable(card, activity);
            JLabel name = new JLabel(activity.getName());
            name.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
            name.setForeground(SwingTheme.NAVY);
            card.add(name, BorderLayout.NORTH);
            JLabel details = new JLabel(activity.getCategory() + " - "
                    + activity.getLocation().getAddress());
            details.setFont(SwingTheme.SMALL);
            details.setForeground(SwingTheme.MUTED);
            card.add(details, BorderLayout.CENTER);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            actions.setOpaque(false);
            JButton remove = SwingTheme.secondaryButton("Remove bookmark");
            remove.setEnabled(controller != null);
            remove.addActionListener(event -> controller.remove(activity.getId()));
            actions.add(remove);
            JButton add = SwingTheme.primaryButton("Add to plan");
            add.setEnabled(manualPlan != null);
            add.addActionListener(event -> {
                if (dayPlan != null && tripOptions != null) {
                    AddToPlanDialog.open(
                            this, activity, dayPlan, tripOptions, manualPlan);
                } else {
                    manualPlan.add(activity.getId(), "");
                }
            });
            actions.add(add);
            card.add(actions, BorderLayout.SOUTH);
            list.add(card);
            list.add(Box.createVerticalStrut(8));
        }
        list.revalidate();
        list.repaint();
    }

    private void makeSelectable(JPanel card, Activity activity) {
        if (selection == null) return;
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setToolTipText("Show " + activity.getName() + " on the map");
        if (activity.getId().equals(selection.getSelectedActivityId())) {
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(SwingTheme.BLUE, 2),
                    BorderFactory.createEmptyBorder(11, 13, 11, 13)));
        }
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                selection.select(activity.getId());
            }
        });
    }
}
