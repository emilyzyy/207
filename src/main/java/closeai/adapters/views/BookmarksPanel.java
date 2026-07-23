package closeai.adapters.views;

import closeai.adapters.viewmodels.BookmarksState;
import closeai.adapters.viewmodels.BookmarksViewModel;
import closeai.domain.entities.Activity;
import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Seeded bookmarks skeleton, deliberately separate from optimization input. */
public final class BookmarksPanel extends JPanel {
    private final BookmarksViewModel viewModel;
    private final JPanel list = new JPanel();

    public BookmarksPanel(BookmarksViewModel viewModel) {
        this.viewModel = viewModel;
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
        JLabel copy = new JLabel(
                "Bookmarks are not automatically included in Optimize Itinerary.");
        copy.setFont(SwingTheme.SMALL);
        copy.setForeground(SwingTheme.MUTED);
        heading.add(copy);
        add(heading, BorderLayout.NORTH);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(SwingTheme.PANEL);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        JLabel notice = new JLabel("Bookmark actions · Not wired for this milestone");
        notice.setFont(SwingTheme.SMALL);
        notice.setForeground(SwingTheme.MUTED);
        add(notice, BorderLayout.SOUTH);
        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> render(viewModel.getState()));
    }

    private void render(BookmarksState state) {
        list.removeAll();
        for (Activity activity : state.getBookmarks()) {
            JPanel card = new JPanel(new BorderLayout());
            SwingTheme.styleCard(card);
            JLabel name = new JLabel(activity.getName());
            name.setFont(SwingTheme.BODY.deriveFont(Font.BOLD));
            name.setForeground(SwingTheme.NAVY);
            card.add(name, BorderLayout.NORTH);
            JLabel details = new JLabel(activity.getCategory() + " · "
                    + activity.getLocation().getAddress());
            details.setFont(SwingTheme.SMALL);
            details.setForeground(SwingTheme.MUTED);
            card.add(details, BorderLayout.CENTER);
            list.add(card);
            list.add(Box.createVerticalStrut(8));
        }
        list.revalidate();
        list.repaint();
    }
}
