package closeai.adapters.views;

import closeai.adapters.controllers.BookmarkController;
import closeai.adapters.controllers.ManualPlanController;
import closeai.adapters.viewmodels.BookmarksState;
import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.domain.entities.Activity;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
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
    private final JPanel list = new JPanel();

    public BookmarksPanel(BookmarksViewModel viewModel) {
        this(viewModel, null, null);
    }

    public BookmarksPanel(BookmarksViewModel viewModel, BookmarkController controller) {
        this(viewModel, controller, null);
    }

    public BookmarksPanel(BookmarksViewModel viewModel, BookmarkController controller,
                          ManualPlanController manualPlan) {
        this.viewModel = viewModel;
        this.controller = controller;
        this.manualPlan = manualPlan;
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
                String start = JOptionPane.showInputDialog(
                        this, "Preferred start time for " + activity.getName()
                                + " (HH:MM). Leave blank for the next available time.",
                        "Add activity to Day Plan", JOptionPane.PLAIN_MESSAGE);
                if (start != null) {
                    manualPlan.add(activity.getId(), start);
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
}
