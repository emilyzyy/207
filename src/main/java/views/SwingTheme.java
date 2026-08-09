package views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.ListCellRenderer;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import entity.valueobjects.ActivityCategory;

/** Shared visual constants derived from the retained web prototype. */
public final class SwingTheme {
    public static Color NAVY = new Color(13, 35, 64);
    public static final Color BLUE = new Color(31, 104, 225);
    public static Color BLUE_SOFT = new Color(238, 245, 255);
    public static Color BACKGROUND = new Color(244, 247, 250);
    public static Color PANEL = Color.WHITE;
    public static Color LINE = new Color(216, 224, 232);
    public static Color MUTED = new Color(91, 106, 123);
    public static Color SUCCESS = new Color(26, 127, 83);
    public static Color ERROR = new Color(181, 56, 48);
    /** Warning band: amber enough to read as a caution, quiet enough not to shout. */
    public static Color WARNING = new Color(146, 94, 6);
    public static Color WARNING_SOFT = new Color(255, 248, 230);
    /** Surface for generated travel rows, so they sit below activities without a border. */
    public static Color TRAVEL_SURFACE = new Color(247, 249, 252);
    private static final Object LIGHT_BUTTON_UI = UIManager.get("ButtonUI");
    private static final Object LIGHT_TABBED_PANE_UI = UIManager.get("TabbedPaneUI");
    private static boolean darkMode;
    private static java.util.Map<ActivityCategory, Color> categorySurfaces = lightCategories();
    private static final String FONT_FAMILY = availableFont("Inter") ? "Inter" : "SansSerif";
    public static final Font TITLE = new Font(FONT_FAMILY, Font.BOLD, 24);
    public static final Font HEADING = new Font(FONT_FAMILY, Font.BOLD, 17);
    public static final Font BODY = new Font(FONT_FAMILY, Font.PLAIN, 13);
    public static final Font SMALL = new Font(FONT_FAMILY, Font.PLAIN, 11);

    static {
        installDefaults();
    }

    private SwingTheme() {
    }

    public static boolean isInterAvailable() { return "Inter".equals(FONT_FAMILY); }

    private static boolean availableFont(String family) {
        for (String installed : java.awt.GraphicsEnvironment
                .getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (family.equalsIgnoreCase(installed)) return true;
        }
        return false;
    }

    public static Color categorySurface(ActivityCategory category) {
        return category == null ? PANEL : categorySurfaces.getOrDefault(category, PANEL);
    }

    public static boolean isDarkMode() { return darkMode; }

    /** Applies the theme-controlled field, popup, and arrow to a combo box. */
    public static void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setUI(new ThemedComboBoxUI());
        comboBox.setBackground(PANEL);
        comboBox.setForeground(NAVY);
        comboBox.setFont(BODY);
        comboBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static void setDarkMode(boolean enabled) {
        if (darkMode == enabled) return;
        Color oldPanel = PANEL, oldBackground = BACKGROUND, oldSoft = BLUE_SOFT;
        Color oldLine = LINE;
        Color oldTravel = TRAVEL_SURFACE, oldWarningSoft = WARNING_SOFT;
        Color oldNavy = NAVY, oldMuted = MUTED, oldSuccess = SUCCESS;
        Color oldError = ERROR, oldWarning = WARNING;
        java.util.Map<ActivityCategory, Color> oldCategories = categorySurfaces;
        darkMode = enabled;
        if (enabled) {
            NAVY = new Color(232, 238, 247); BACKGROUND = new Color(18, 24, 32);
            PANEL = new Color(28, 36, 48); LINE = new Color(57, 70, 86);
            MUTED = new Color(174, 187, 202); BLUE_SOFT = new Color(38, 54, 75);
            SUCCESS = new Color(92, 201, 143); ERROR = new Color(255, 133, 124);
            WARNING = new Color(240, 190, 92); WARNING_SOFT = new Color(65, 51, 28);
            TRAVEL_SURFACE = new Color(34, 43, 55); categorySurfaces = darkCategories();
        } else {
            NAVY = new Color(13, 35, 64); BACKGROUND = new Color(244, 247, 250);
            PANEL = Color.WHITE; LINE = new Color(216, 224, 232);
            MUTED = new Color(91, 106, 123); BLUE_SOFT = new Color(238, 245, 255);
            SUCCESS = new Color(26, 127, 83); ERROR = new Color(181, 56, 48);
            WARNING = new Color(146, 94, 6); WARNING_SOFT = new Color(255, 248, 230);
            TRAVEL_SURFACE = new Color(247, 249, 252); categorySurfaces = lightCategories();
        }
        installDefaults();
        java.util.Map<Color, Color> backgrounds = new java.util.HashMap<>();
        backgrounds.put(oldPanel, PANEL); backgrounds.put(oldBackground, BACKGROUND);
        backgrounds.put(oldSoft, BLUE_SOFT); backgrounds.put(oldTravel, TRAVEL_SURFACE);
        backgrounds.put(oldWarningSoft, WARNING_SOFT);
        for (ActivityCategory category : ActivityCategory.values()) {
            backgrounds.put(oldCategories.get(category), categorySurfaces.get(category));
        }
        java.util.Map<Color, Color> foregrounds = new java.util.HashMap<>();
        foregrounds.put(oldNavy, NAVY); foregrounds.put(oldMuted, MUTED);
        foregrounds.put(oldSuccess, SUCCESS); foregrounds.put(oldError, ERROR);
        foregrounds.put(oldWarning, WARNING);
        for (java.awt.Window window : java.awt.Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            recolor(window, backgrounds, foregrounds, oldLine);
            window.revalidate();
            window.repaint();
        }
    }

    private static void recolor(Component component, java.util.Map<Color, Color> backgrounds,
                                java.util.Map<Color, Color> foregrounds, Color oldLine) {
        Color nextBackground = backgrounds.get(component.getBackground());
        if (nextBackground != null) component.setBackground(nextBackground);
        Color nextForeground = foregrounds.get(component.getForeground());
        if (nextForeground != null) component.setForeground(nextForeground);

        if (component instanceof JComponent) {
            JComponent swingComponent = (JComponent) component;
            swingComponent.setBorder(recolorBorder(swingComponent.getBorder(), oldLine));
        }
        if (component instanceof JTextComponent) {
            JTextComponent text = (JTextComponent) component;
            text.setBackground(PANEL);
            text.setForeground(NAVY);
            text.setCaretColor(NAVY);
            text.setSelectionColor(BLUE);
            text.setSelectedTextColor(Color.WHITE);
        } else if (component instanceof JComboBox) {
            styleComboBox((JComboBox<?>) component);
        } else if (component instanceof JTabbedPane) {
            ((JTabbedPane) component).setUI(new MinimalTabbedPaneUI());
            component.setBackground(PANEL);
            component.setForeground(NAVY);
        }
        if (component instanceof JButton && !(component instanceof ThemeToggleButton)) {
            themeButton((JButton) component);
        }
        if (component instanceof JScrollPane) {
            JScrollPane pane = (JScrollPane) component;
            pane.getViewport().setBackground(pane.getViewport().getView() == null
                    ? PANEL : pane.getViewport().getView().getBackground());
        }
        if (component instanceof JScrollBar) {
            ((JScrollBar) component).setUI(new ThemedScrollBarUI());
            component.setBackground(BACKGROUND);
        }
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                recolor(child, backgrounds, foregrounds, oldLine);
            }
        }
    }

    private static Border recolorBorder(Border border, Color oldLine) {
        if (border instanceof LineBorder) {
            LineBorder line = (LineBorder) border;
            Color color = line.getLineColor().equals(oldLine) ? LINE : line.getLineColor();
            return BorderFactory.createLineBorder(color, line.getThickness(), line.getRoundedCorners());
        }
        if (border instanceof CompoundBorder) {
            CompoundBorder compound = (CompoundBorder) border;
            return BorderFactory.createCompoundBorder(
                    recolorBorder(compound.getOutsideBorder(), oldLine),
                    recolorBorder(compound.getInsideBorder(), oldLine));
        }
        return border;
    }

    private static void themeButton(JButton button) {
        // WindowsButtonUI ignores custom dark backgrounds. BasicButtonUI paints the
        // component colours we assign, and updateComponentTreeUI restores the native
        // delegate automatically when the application returns to light mode.
        button.setUI(new RoundedButtonUI());
        button.setOpaque(false);
        button.setContentAreaFilled(true);
        Object role = button.getClientProperty("trippy.buttonRole");
        if ("primary".equals(role)) {
            button.setBackground(button.isEnabled() ? BLUE : LINE);
            button.setForeground(button.isEnabled() ? Color.WHITE : MUTED);
            return;
        }
        button.setBackground(PANEL);
        button.setForeground(button.isEnabled() ? NAVY : MUTED);
        if (!(button.getBorder() instanceof CompoundBorder)) {
            button.setBorder(BorderFactory.createLineBorder(LINE));
        }
    }

    private static void installDefaults() {
        UIManager.put("ButtonUI", RoundedButtonUI.class.getName());
        // Use one predictable renderer for the closed field and popup in both themes.
        // WindowsComboBoxUI otherwise retains native white fields and stale popup colours.
        UIManager.put("ComboBoxUI", ThemedComboBoxUI.class.getName());
        UIManager.put("TabbedPaneUI", darkMode
                ? BasicTabbedPaneUI.class.getName() : LIGHT_TABBED_PANE_UI);
        UIManager.put("Panel.background", PANEL);
        UIManager.put("Label.foreground", NAVY);
        UIManager.put("TextField.background", PANEL);
        UIManager.put("TextField.foreground", NAVY);
        UIManager.put("TextField.caretForeground", NAVY);
        UIManager.put("TextField.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE, 1, true),
                BorderFactory.createEmptyBorder(4, 7, 4, 7)));
        UIManager.put("TextField.inactiveBackground", BACKGROUND);
        UIManager.put("FormattedTextField.background", PANEL);
        UIManager.put("FormattedTextField.foreground", NAVY);
        UIManager.put("ComboBox.background", PANEL);
        UIManager.put("ComboBox.foreground", NAVY);
        UIManager.put("ComboBox.selectionBackground", BLUE_SOFT);
        UIManager.put("ComboBox.selectionForeground", NAVY);
        UIManager.put("ComboBox.border", BorderFactory.createLineBorder(LINE, 1, true));
        UIManager.put("Button.background", PANEL);
        UIManager.put("Button.foreground", NAVY);
        Cursor pointer = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        UIManager.put("Button.cursor", pointer);
        UIManager.put("ComboBox.cursor", pointer);
        UIManager.put("CheckBox.cursor", pointer);
        UIManager.put("TabbedPane.cursor", pointer);
        UIManager.put("Spinner.cursor", pointer);
        UIManager.put("Button.font", BODY);
        UIManager.put("Label.font", BODY);
        UIManager.put("ComboBox.font", BODY);
        UIManager.put("TextField.font", BODY);
        UIManager.put("Button.disabledText", MUTED);
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("Viewport.background", PANEL);
        UIManager.put("ScrollBar.background", BACKGROUND);
        UIManager.put("ScrollBar.track", BACKGROUND);
        UIManager.put("ScrollBar.thumb", LINE);
        UIManager.put("TabbedPane.background", PANEL);
        UIManager.put("TabbedPane.foreground", NAVY);
        UIManager.put("TabbedPane.selected", BACKGROUND);
        UIManager.put("TabbedPane.contentAreaColor", PANEL);
        UIManager.put("TabbedPane.highlight", LINE);
        UIManager.put("TabbedPane.light", LINE);
        UIManager.put("TabbedPane.shadow", LINE);
        UIManager.put("TabbedPane.darkShadow", LINE);
        UIManager.put("TabbedPane.focus", BLUE);
    }

    /** Combo-box delegate whose field, popup rows, and arrow all follow this theme. */
    public static final class ThemedComboBoxUI extends BasicComboBoxUI {
        public static ComponentUI createUI(JComponent component) {
            return new ThemedComboBoxUI();
        }

        @Override
        protected ListCellRenderer<Object> createRenderer() {
            return new ThemedComboBoxRenderer();
        }

        @Override
        protected JButton createArrowButton() {
            JButton button = new JButton("\u25be");
            button.setFont(SMALL);
            button.setMargin(new java.awt.Insets(0, 6, 0, 6));
            button.setFocusable(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            themeButton(button);
            return button;
        }
    }

    /** Removes the heavy native content frame around the planner tabs. */
    public static final class MinimalTabbedPaneUI extends BasicTabbedPaneUI {
        @Override protected void paintContentBorder(Graphics g, int placement, int selected) { }
    }

    /** Paints every themed button as one quiet rounded control in both themes. */
    public static final class RoundedButtonUI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent component) {
            return new RoundedButtonUI();
        }
        @Override public void paint(Graphics graphics, JComponent component) {
            JButton button = (JButton) component;
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = button.isEnabled() ? button.getBackground() : LINE;
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 14, 14);
            if (!"primary".equals(button.getClientProperty("trippy.buttonRole"))) {
                g2.setColor(LINE);
                g2.drawRoundRect(0, 0, component.getWidth() - 1,
                        component.getHeight() - 1, 14, 14);
            }
            g2.dispose();
            button.setContentAreaFilled(false);
            super.paint(graphics, component);
        }
    }

    private static final class ThemedComboBoxRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected,
                                                       boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            label.setBackground(isSelected ? BLUE_SOFT : PANEL);
            label.setForeground(list.isEnabled() ? NAVY : MUTED);
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return label;
        }
    }

    private static final class ThemedScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            trackColor = BACKGROUND;
            thumbColor = darkMode ? new Color(85, 101, 120) : LINE;
            thumbDarkShadowColor = thumbColor;
            thumbHighlightColor = thumbColor;
            thumbLightShadowColor = thumbColor;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return scrollButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return scrollButton();
        }

        private JButton scrollButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setBorder(null);
            button.setBackground(BACKGROUND);
            return button;
        }
    }

    private static java.util.Map<ActivityCategory, Color> lightCategories() {
        java.util.Map<ActivityCategory, Color> colors = new java.util.EnumMap<>(ActivityCategory.class);
        colors.put(ActivityCategory.FOOD, new Color(255, 238, 218));
        colors.put(ActivityCategory.MUSEUM, new Color(242, 230, 255));
        colors.put(ActivityCategory.SHOPPING, new Color(255, 229, 240));
        colors.put(ActivityCategory.COFFEE, new Color(241, 226, 207));
        colors.put(ActivityCategory.ATTRACTION, new Color(255, 246, 194));
        colors.put(ActivityCategory.ENTERTAINMENT, new Color(255, 224, 224));
        colors.put(ActivityCategory.PARKS_NATURE, new Color(224, 244, 226));
        colors.put(ActivityCategory.HISTORIC, new Color(239, 229, 207));
        colors.put(ActivityCategory.SPORTS_RECREATION, new Color(218, 243, 232));
        colors.put(ActivityCategory.ARTS_CULTURE, new Color(250, 225, 215));
        return colors;
    }

    private static java.util.Map<ActivityCategory, Color> darkCategories() {
        java.util.Map<ActivityCategory, Color> colors = new java.util.EnumMap<>(ActivityCategory.class);
        colors.put(ActivityCategory.FOOD, new Color(72, 52, 36));
        colors.put(ActivityCategory.MUSEUM, new Color(58, 43, 73));
        colors.put(ActivityCategory.SHOPPING, new Color(70, 42, 56));
        colors.put(ActivityCategory.COFFEE, new Color(65, 51, 40));
        colors.put(ActivityCategory.ATTRACTION, new Color(70, 62, 31));
        colors.put(ActivityCategory.ENTERTAINMENT, new Color(73, 42, 43));
        colors.put(ActivityCategory.PARKS_NATURE, new Color(38, 65, 45));
        colors.put(ActivityCategory.HISTORIC, new Color(63, 54, 38));
        colors.put(ActivityCategory.SPORTS_RECREATION, new Color(34, 65, 56));
        colors.put(ActivityCategory.ARTS_CULTURE, new Color(70, 47, 40));
        return colors;
    }

    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE, 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14));
    }

    public static void styleCard(JComponent component) {
        component.setBackground(PANEL);
        component.setBorder(cardBorder());
    }

    public static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("trippy.buttonRole", "primary");
        button.setFont(BODY.deriveFont(Font.BOLD));
        button.setForeground(Color.WHITE);
        button.setBackground(BLUE);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setUI(new RoundedButtonUI());
        return button;
    }

    public static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("trippy.buttonRole", "secondary");
        button.setFont(BODY);
        button.setForeground(NAVY);
        button.setBackground(PANEL);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setUI(new RoundedButtonUI());
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        return button;
    }

    /**
     * A small status pill, e.g. {@code Locked} or {@code Moved}.
     *
     * <p>The text carries the meaning; the tint is decoration. That ordering matters — the
     * Preview previously wrote " [locked]" into an HTML label, which is readable but reads
     * as an afterthought rather than a state.</p>
     */
    public static JLabel badge(String text, Color ink, Color surface) {
        JLabel badge = new JLabel(text);
        badge.setFont(SMALL.deriveFont(Font.BOLD));
        badge.setForeground(ink);
        badge.setBackground(surface);
        badge.setOpaque(true);
        badge.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
        badge.getAccessibleContext().setAccessibleName(text);
        return badge;
    }

    /** A section heading with a rule running to the right of it. */
    public static JPanel sectionHeader(String title, String trailing, Color accent) {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(title);
        label.setFont(SMALL.deriveFont(Font.BOLD));
        label.setForeground(accent);
        header.add(label, BorderLayout.WEST);

        if (trailing != null && !trailing.isEmpty()) {
            JLabel note = new JLabel(trailing);
            note.setFont(SMALL);
            note.setForeground(MUTED);
            header.add(note, BorderLayout.EAST);
        }
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                header.getPreferredSize().height));
        return header;
    }

    /**
     * One before/after figure, as a small card: a bold {@code 0 → 12} over a caption.
     * Replaces a run-on sentence of numbers with three things the eye can compare.
     */
    public static JPanel metricCard(String value, String caption) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(BLUE_SOFT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(214, 229, 250)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        JLabel figure = new JLabel(value);
        figure.setFont(BODY.deriveFont(Font.BOLD));
        figure.setForeground(NAVY);
        figure.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(figure);

        JLabel label = new JLabel(caption);
        label.setFont(SMALL);
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(label);

        card.getAccessibleContext().setAccessibleName(caption + ": " + value);
        return card;
    }

    /** A caution strip, visually separate from both the figures and the reasoning. */
    public static JPanel warningBand(java.util.List<String> messages) {
        JPanel band = new JPanel();
        band.setLayout(new BoxLayout(band, BoxLayout.Y_AXIS));
        band.setBackground(WARNING_SOFT);
        band.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, WARNING),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        band.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (String message : messages) {
            JLabel line = new JLabel("<html>&#9888; " + message
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    + "</html>");
            line.setFont(SMALL);
            line.setForeground(WARNING);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            band.add(line);
        }
        band.getAccessibleContext().setAccessibleName(
                messages.size() + " scheduling warning" + (messages.size() == 1 ? "" : "s"));
        return band;
    }

    public static JButton placeholderButton(String text) {
        JButton button = new JButton(text);
        button.setFont(SMALL);
        button.setEnabled(false);
        button.setToolTipText("Not wired for this milestone");
        return button;
    }
}
