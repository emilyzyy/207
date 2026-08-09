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
 * <p>Cards are compact rounded tiles laid out two to a row, so the strongest few read as a
 * small grid to be scanned rather than a column of banners to be waded through. The whole
 * thing keeps a fixed preferred width and drops into a {@code BorderLayout.EAST} unchanged;
 * at narrow widths the host places it above the schedule instead, which is the host's
 * decision, and this panel looks the same either way.</p>
 *
 * <p>Only the strongest few are shown. A wall of cards is not a hierarchy, and the fifth
 * most important thing about a schedule is not worth the room it takes from the first.</p>
 *
 * <p>Nothing negative appears here. Travel that grew, waiting that grew, an activity pushed
 * out of daylight — those are real and belong under "Why these changes?" with the full
 * before/after figures, not dressed as an achievement.</p>
 */
public final class ScheduleImprovementsPanel extends JPanel {

    /** Wide enough for two tiles side by side, narrow enough to sit beside a day. */
    static final int PREFERRED_WIDTH = 360;

    /** Past this the tiles stop being a summary and become a list. */
    static final int MOST_SHOWN = 4;

    /**
     * Wrap width inside one tile. Swing will not wrap an html label without being told how
     * wide it may be, and without this the longest headline set the width of the grid and
     * everything past it was cut off.
     */
    private static final int TEXT_WIDTH = (PREFERRED_WIDTH - 6) / 2 - 46;

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
            add(tileGrid(improvements));
            if (improvements.size() > MOST_SHOWN) {
                JLabel more = new JLabel("<html>and " + (improvements.size() - MOST_SHOWN)
                        + " more, under Why these changes?</html>");
                more.setFont(SwingTheme.SMALL);
                more.setForeground(SwingTheme.MUTED);
                more.setAlignmentX(Component.LEFT_ALIGNMENT);
                more.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 2));
                add(more);
            }
        }
        add(javax.swing.Box.createVerticalGlue());
        setPreferredSize(new Dimension(PREFERRED_WIDTH, getPreferredSize().height));
        setMaximumSize(new Dimension(PREFERRED_WIDTH, Integer.MAX_VALUE));
    }

    /** The strongest few tiles, two to a row. */
    private static JPanel tileGrid(List<ImprovementView> improvements) {
        int shown = Math.min(MOST_SHOWN, improvements.size());
        int rows = (shown + 1) / 2;
        JPanel grid = new JPanel(new java.awt.GridLayout(rows, 2, 6, 6));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < shown; i++) {
            grid.add(card(improvements.get(i)));
        }
        // GridLayout gives every cell the same size, so an odd count needs a spacer or the
        // last tile stretches to twice the width of the others.
        if (shown % 2 == 1) {
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            grid.add(filler);
        }
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        return grid;
    }

    /**
     * One tile: a glyph and figure on the top line, one quiet line under it.
     *
     * <p>Flat and softly rounded, sized by the grid rather than by its own text, so four
     * tiles read as a set rather than as four differently-shaped notices.</p>
     */
    private static JPanel card(ImprovementView improvement) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(SwingTheme.BLUE_SOFT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SwingTheme.LINE, 1, true),
                BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // The glyph leads the figure rather than sitting in its own colour swatch, so the
        // category survives being read aloud or printed in grey.
        JLabel primary = new JLabel("<html><div style='width:" + TEXT_WIDTH + "px'>"
                + improvement.getMarker() + "&nbsp; <b>" + escape(improvement.getPrimary())
                + "</b></div></html>");
        primary.setFont(SwingTheme.SMALL);
        primary.setForeground(SwingTheme.NAVY);
        primary.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(primary);

        if (!improvement.getSecondary().isEmpty()) {
            JLabel secondary = new JLabel("<html><div style='width:" + TEXT_WIDTH + "px'>"
                    + escape(improvement.getSecondary()) + "</div></html>");
            secondary.setFont(SwingTheme.SMALL);
            secondary.setForeground(SwingTheme.MUTED);
            secondary.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(secondary);
        }

        card.getAccessibleContext().setAccessibleName(improvement.spoken());
        card.setToolTipText(improvement.spoken());
        return card;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
