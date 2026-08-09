package views;

import interface_adapter.viewmodels.ImprovementView;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The "Schedule improvements" stack: one card per thing the schedule provably achieved.
 *
 * <p>Deliberately standalone and told nothing but a list of already-worded cards. The Day
 * Plan is expected to become a full 12 a.m.–12 a.m. calendar, and when it does this panel
 * should move to the side of that timeline without being rewritten — so it knows nothing
 * about schedules, rows, metrics or the panel currently hosting it.</p>
 *
 * <p>Cards stack vertically at a fixed preferred width so the whole thing drops into a
 * {@code BorderLayout.EAST} unchanged. At narrow widths the host places it below the
 * schedule instead; that is the host's decision, and this panel looks the same either way.</p>
 *
 * <p>Nothing negative appears here. Travel that grew, waiting that grew, an activity pushed
 * out of daylight — those are real and belong under "Why this schedule?" with the full
 * before/after figures, not dressed as an achievement.</p>
 */
public final class ScheduleImprovementsPanel extends JPanel {

    /** Wide enough for a headline without wrapping, narrow enough to sit beside a day. */
    static final int PREFERRED_WIDTH = 250;

    /** Shown when the schedule is valid but genuinely improved nothing measurable. */
    static final String NOTHING_IMPROVED = "No major timing improvements were identified.";

    public ScheduleImprovementsPanel(List<ImprovementView> improvements) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setAlignmentY(Component.TOP_ALIGNMENT);
        getAccessibleContext().setAccessibleName("Schedule improvements");

        add(SwingTheme.sectionHeader("SCHEDULE IMPROVEMENTS", "", SwingTheme.NAVY));
        add(javax.swing.Box.createVerticalStrut(8));

        if (improvements == null || improvements.isEmpty()) {
            // An honest empty state. Inventing a card here would make every other card
            // worth less, because the user could no longer tell which ones were earned.
            JLabel none = new JLabel("<html>" + NOTHING_IMPROVED + "</html>");
            none.setFont(SwingTheme.SMALL);
            none.setForeground(SwingTheme.MUTED);
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            none.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
            add(none);
        } else {
            for (ImprovementView improvement : improvements) {
                add(card(improvement));
                add(javax.swing.Box.createVerticalStrut(6));
            }
        }
        add(javax.swing.Box.createVerticalGlue());
        setPreferredSize(new Dimension(PREFERRED_WIDTH, getPreferredSize().height));
        setMaximumSize(new Dimension(PREFERRED_WIDTH, Integer.MAX_VALUE));
    }

    private static JPanel card(ImprovementView improvement) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(SwingTheme.PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, SwingTheme.SUCCESS),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 1, 1, 1, SwingTheme.LINE),
                        BorderFactory.createEmptyBorder(8, 10, 8, 10))));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // The glyph leads the headline rather than sitting in its own colour swatch, so the
        // category survives being read aloud or printed in grey.
        JLabel headline = new JLabel("<html><b>" + improvement.getMarker() + "&nbsp; "
                + escape(improvement.getHeadline()) + "</b></html>");
        headline.setFont(SwingTheme.SMALL);
        headline.setForeground(SwingTheme.NAVY);
        headline.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(headline);

        if (!improvement.getDetail().isEmpty()) {
            JLabel detail = new JLabel("<html>" + escape(improvement.getDetail()) + "</html>");
            detail.setFont(SwingTheme.SMALL);
            detail.setForeground(SwingTheme.MUTED);
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(detail);
        }

        String spoken = improvement.getHeadline()
                + (improvement.getDetail().isEmpty() ? "" : ", " + improvement.getDetail());
        card.getAccessibleContext().setAccessibleName(spoken);
        card.setToolTipText(spoken);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
