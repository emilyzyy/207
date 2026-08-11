package views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.JTextComponent;

import entity.valueobjects.ActivityCategory;

/** Shared visual constants derived from the retained web prototype. */
public final class SwingTheme {

    // The two palettes, each stated once. The mutable fields below name whichever palette is
    // currently in force; before this they carried their light values inline and repeated them
    // again when the theme switched back, so the same colour was written in two places.
    private static final Color NAVY_LIGHT = new Color(13, 35, 64);
    private static final Color NAVY_DARK = new Color(232, 238, 247);
    private static final Color BLUE_SOFT_LIGHT = new Color(238, 245, 255);
    private static final Color BLUE_SOFT_DARK = new Color(38, 54, 75);
    private static final Color BACKGROUND_LIGHT = new Color(244, 247, 250);
    private static final Color BACKGROUND_DARK = new Color(18, 24, 32);
    private static final Color PANEL_LIGHT = Color.WHITE;
    private static final Color PANEL_DARK = new Color(28, 36, 48);
    private static final Color LINE_LIGHT = new Color(216, 224, 232);
    private static final Color LINE_DARK = new Color(57, 70, 86);
    private static final Color MUTED_LIGHT = new Color(91, 106, 123);
    private static final Color MUTED_DARK = new Color(174, 187, 202);
    private static final Color SUCCESS_LIGHT = new Color(26, 127, 83);
    private static final Color SUCCESS_DARK = new Color(92, 201, 143);
    private static final Color ERROR_LIGHT = new Color(181, 56, 48);
    private static final Color ERROR_DARK = new Color(255, 133, 124);
    private static final Color WARNING_LIGHT = new Color(146, 94, 6);
    private static final Color WARNING_DARK = new Color(240, 190, 92);
    private static final Color WARNING_SOFT_LIGHT = new Color(255, 248, 230);
    private static final Color WARNING_SOFT_DARK = new Color(65, 51, 28);
    private static final Color TRAVEL_SURFACE_LIGHT = new Color(247, 249, 252);
    private static final Color TRAVEL_SURFACE_DARK = new Color(34, 43, 55);

    // One surface tint per activity category, in both themes.
    private static final Color FOOD_LIGHT = new Color(255, 238, 218);
    private static final Color FOOD_DARK = new Color(72, 52, 36);
    private static final Color MUSEUM_LIGHT = new Color(242, 230, 255);
    private static final Color MUSEUM_DARK = new Color(58, 43, 73);
    private static final Color SHOPPING_LIGHT = new Color(255, 229, 240);
    private static final Color SHOPPING_DARK = new Color(70, 42, 56);
    private static final Color COFFEE_LIGHT = new Color(241, 226, 207);
    private static final Color COFFEE_DARK = new Color(65, 51, 40);
    private static final Color ATTRACTION_LIGHT = new Color(255, 246, 194);
    private static final Color ATTRACTION_DARK = new Color(70, 62, 31);
    private static final Color ENTERTAINMENT_LIGHT = new Color(255, 224, 224);
    private static final Color ENTERTAINMENT_DARK = new Color(73, 42, 43);
    private static final Color PARKS_LIGHT = new Color(224, 244, 226);
    private static final Color PARKS_DARK = new Color(38, 65, 45);
    private static final Color HISTORIC_LIGHT = new Color(239, 229, 207);
    private static final Color HISTORIC_DARK = new Color(63, 54, 38);
    private static final Color SPORTS_LIGHT = new Color(218, 243, 232);
    private static final Color SPORTS_DARK = new Color(34, 65, 56);
    private static final Color ARTS_LIGHT = new Color(250, 225, 215);
    private static final Color ARTS_DARK = new Color(70, 47, 40);
    private static final Color SCROLL_THUMB_DARK = new Color(85, 101, 120);

    // Padding used by the shared component builders.
    private static final int CHIP_PAD_Y = 4;
    private static final int CHIP_PAD_X = 7;
    private static final int BUTTON_MARGIN_X = 6;
    private static final int CORNER_RADIUS = 14;
    private static final int BADGE_PAD_Y = 3;
    private static final int BADGE_PAD_X = 6;
    private static final int CARD_PAD_Y = 12;
    private static final int CARD_PAD_X = 14;
    private static final int PRIMARY_PAD_Y = 10;
    private static final int PRIMARY_PAD_X = 16;
    private static final int FIELD_PAD_Y = 7;
    private static final int FIELD_PAD_X = 12;
    private static final int PILL_PAD_Y = 2;
    private static final int PILL_PAD_X = 7;
    private static final int NOTICE_PAD_Y = 8;
    private static final int NOTICE_PAD_X = 12;
    private static final int NOTICE_STRIPE = 3;
    private static final int NOTICE_INNER_X = 10;

    public static Color NAVY = NAVY_LIGHT;
    public static final Color BLUE = new Color(31, 104, 225);
    public static Color BLUE_SOFT = BLUE_SOFT_LIGHT;
    public static Color BACKGROUND = BACKGROUND_LIGHT;
    public static Color PANEL = PANEL_LIGHT;
    public static Color LINE = LINE_LIGHT;
    public static Color MUTED = MUTED_LIGHT;
    public static Color SUCCESS = SUCCESS_LIGHT;
    public static Color ERROR = ERROR_LIGHT;
    /** Warning band: amber enough to read as a caution, quiet enough not to shout. */
    public static Color WARNING = WARNING_LIGHT;
    public static Color WARNING_SOFT = WARNING_SOFT_LIGHT;
    /** Surface for generated travel rows, so they sit below activities without a border. */
    public static Color TRAVEL_SURFACE = TRAVEL_SURFACE_LIGHT;
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

    public static boolean isInterAvailable() {
        return "Inter".equals(FONT_FAMILY);
    }

    private static boolean availableFont(String family) {
        for (String installed : java.awt.GraphicsEnvironment
                .getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (family.equalsIgnoreCase(installed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Performs the c at eg or ys ur fa ce operation.
     * @param category the c at eg or y value
     * @return the result of the operation
     */
    public static Color categorySurface(ActivityCategory category) {
        return category == null ? PANEL : categorySurfaces.getOrDefault(category, PANEL);
    }

    public static boolean isDarkMode() {
        return darkMode;
    }
    /**
     * Applies the theme-controlled field, popup, and arrow to a combo box.
     * @param comboBox the c om bo bo x value
     */

    public static void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setUI(new ThemedComboBoxUI());
        comboBox.setBackground(PANEL);
        comboBox.setForeground(NAVY);
        comboBox.setFont(BODY);
        comboBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * Performs the s et da rk mo de operation.
     * @param enabled the e na bl ed value
     */
    public static void setDarkMode(boolean enabled) {
        if (darkMode == enabled) {
            return;
        }
        final Color oldPanel = PANEL, oldBackground = BACKGROUND, oldSoft = BLUE_SOFT;
        final Color oldLine = LINE;
        final Color oldTravel = TRAVEL_SURFACE, oldWarningSoft = WARNING_SOFT;
        final Color oldNavy = NAVY, oldMuted = MUTED, oldSuccess = SUCCESS;
        final Color oldError = ERROR, oldWarning = WARNING;
        final java.util.Map<ActivityCategory, Color> oldCategories = categorySurfaces;
        darkMode = enabled;
        if (enabled) {
            NAVY = NAVY_DARK;
            BACKGROUND = BACKGROUND_DARK;
            PANEL = PANEL_DARK;
            LINE = LINE_DARK;
            MUTED = MUTED_DARK;
            BLUE_SOFT = BLUE_SOFT_DARK;
            SUCCESS = SUCCESS_DARK;
            ERROR = ERROR_DARK;
            WARNING = WARNING_DARK;
            WARNING_SOFT = WARNING_SOFT_DARK;
            TRAVEL_SURFACE = TRAVEL_SURFACE_DARK;
            categorySurfaces = darkCategories();
        }
        else {
            NAVY = NAVY_LIGHT;
            BACKGROUND = BACKGROUND_LIGHT;
            PANEL = PANEL_LIGHT;
            LINE = LINE_LIGHT;
            MUTED = MUTED_LIGHT;
            BLUE_SOFT = BLUE_SOFT_LIGHT;
            SUCCESS = SUCCESS_LIGHT;
            ERROR = ERROR_LIGHT;
            WARNING = WARNING_LIGHT;
            WARNING_SOFT = WARNING_SOFT_LIGHT;
            TRAVEL_SURFACE = TRAVEL_SURFACE_LIGHT;
            categorySurfaces = lightCategories();
        }
        installDefaults();
        final java.util.Map<Color, Color> backgrounds = new java.util.HashMap<>();
        backgrounds.put(oldPanel, PANEL);
        backgrounds.put(oldBackground, BACKGROUND);
        backgrounds.put(oldSoft, BLUE_SOFT);
        backgrounds.put(oldTravel, TRAVEL_SURFACE);
        backgrounds.put(oldWarningSoft, WARNING_SOFT);
        for (ActivityCategory category : ActivityCategory.values()) {
            backgrounds.put(oldCategories.get(category), categorySurfaces.get(category));
        }
        final java.util.Map<Color, Color> foregrounds = new java.util.HashMap<>();
        foregrounds.put(oldNavy, NAVY);
        foregrounds.put(oldMuted, MUTED);
        foregrounds.put(oldSuccess, SUCCESS);
        foregrounds.put(oldError, ERROR);
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
        final Color nextBackground = backgrounds.get(component.getBackground());
        if (nextBackground != null) {
            component.setBackground(nextBackground);
        }
        final Color nextForeground = foregrounds.get(component.getForeground());
        if (nextForeground != null) {
            component.setForeground(nextForeground);
        }

        if (component instanceof JComponent) {
            final JComponent swingComponent = (JComponent) component;
            swingComponent.setBorder(recolorBorder(swingComponent.getBorder(), oldLine));
        }
        if (component instanceof JTextComponent) {
            final JTextComponent text = (JTextComponent) component;
            text.setBackground(PANEL);
            text.setForeground(NAVY);
            text.setCaretColor(NAVY);
            text.setSelectionColor(BLUE);
            text.setSelectedTextColor(Color.WHITE);
        }
        else if (component instanceof JComboBox) {
            styleComboBox((JComboBox<?>) component);
        }
        else if (component instanceof JTabbedPane) {
            ((JTabbedPane) component).setUI(new MinimalTabbedPaneUI());
            component.setBackground(PANEL);
            component.setForeground(NAVY);
        }
        if (component instanceof JButton && !(component instanceof ThemeToggleButton)) {
            themeButton((JButton) component);
        }
        if (component instanceof JScrollPane) {
            final JScrollPane pane = (JScrollPane) component;
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
            final LineBorder line = (LineBorder) border;
            final Color color = line.getLineColor().equals(oldLine) ? LINE : line.getLineColor();
            return BorderFactory.createLineBorder(color, line.getThickness(), line.getRoundedCorners());
        }
        if (border instanceof CompoundBorder) {
            final CompoundBorder compound = (CompoundBorder) border;
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
        final Object role = button.getClientProperty("trippy.buttonRole");
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
                BorderFactory.createEmptyBorder(CHIP_PAD_Y, CHIP_PAD_X, CHIP_PAD_Y, CHIP_PAD_X)));
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
        final Cursor pointer = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
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
        /**
         * Performs the c re at eu i operation.
         * @param component the c om po ne nt value
         * @return the result of the operation
         */
        public static ComponentUI createUI(JComponent component) {
            return new ThemedComboBoxUI();
        }

        @Override
        protected ListCellRenderer<Object> createRenderer() {
            return new ThemedComboBoxRenderer();
        }

        @Override
        protected JButton createArrowButton() {
            final JButton button = new JButton("\u25be");
            button.setFont(SMALL);
            button.setMargin(new java.awt.Insets(0, BUTTON_MARGIN_X, 0, BUTTON_MARGIN_X));
            button.setFocusable(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            themeButton(button);
            return button;
        }
    }

    /** Removes the heavy native content frame around the planner tabs. */
    public static final class MinimalTabbedPaneUI extends BasicTabbedPaneUI {
        @Override protected void paintContentBorder(Graphics g, int placement, int selected) {
        }
    }

    /** Paints every themed button as one quiet rounded control in both themes. */
    public static final class RoundedButtonUI extends BasicButtonUI {
        /**
         * Performs the c re at eu i operation.
         * @param component the c om po ne nt value
         * @return the result of the operation
         */
        public static ComponentUI createUI(JComponent component) {
            return new RoundedButtonUI();
        }

        @Override public void paint(Graphics graphics, JComponent component) {
            final JButton button = (JButton) component;
            final Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            final Color fill = button.isEnabled() ? button.getBackground() : LINE;
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), CORNER_RADIUS, CORNER_RADIUS);
            if (!"primary".equals(button.getClientProperty("trippy.buttonRole"))) {
                g2.setColor(LINE);
                g2.drawRoundRect(0, 0, component.getWidth() - 1,
                        component.getHeight() - 1, CORNER_RADIUS, CORNER_RADIUS);
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
            final JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            label.setBackground(isSelected ? BLUE_SOFT : PANEL);
            label.setForeground(list.isEnabled() ? NAVY : MUTED);
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(BADGE_PAD_Y, BADGE_PAD_X, BADGE_PAD_Y, BADGE_PAD_X));
            return label;
        }
    }

    private static final class ThemedScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            trackColor = BACKGROUND;
            thumbColor = darkMode ? SCROLL_THUMB_DARK : LINE;
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
            final JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setBorder(null);
            button.setBackground(BACKGROUND);
            return button;
        }
    }

    private static java.util.Map<ActivityCategory, Color> lightCategories() {
        final java.util.Map<ActivityCategory, Color> colors = new java.util.EnumMap<>(ActivityCategory.class);
        colors.put(ActivityCategory.FOOD, FOOD_LIGHT);
        colors.put(ActivityCategory.MUSEUM, MUSEUM_LIGHT);
        colors.put(ActivityCategory.SHOPPING, SHOPPING_LIGHT);
        colors.put(ActivityCategory.COFFEE, COFFEE_LIGHT);
        colors.put(ActivityCategory.ATTRACTION, ATTRACTION_LIGHT);
        colors.put(ActivityCategory.ENTERTAINMENT, ENTERTAINMENT_LIGHT);
        colors.put(ActivityCategory.PARKS_NATURE, PARKS_LIGHT);
        colors.put(ActivityCategory.HISTORIC, HISTORIC_LIGHT);
        colors.put(ActivityCategory.SPORTS_RECREATION, SPORTS_LIGHT);
        colors.put(ActivityCategory.ARTS_CULTURE, ARTS_LIGHT);
        return colors;
    }

    private static java.util.Map<ActivityCategory, Color> darkCategories() {
        final java.util.Map<ActivityCategory, Color> colors = new java.util.EnumMap<>(ActivityCategory.class);
        colors.put(ActivityCategory.FOOD, FOOD_DARK);
        colors.put(ActivityCategory.MUSEUM, MUSEUM_DARK);
        colors.put(ActivityCategory.SHOPPING, SHOPPING_DARK);
        colors.put(ActivityCategory.COFFEE, COFFEE_DARK);
        colors.put(ActivityCategory.ATTRACTION, ATTRACTION_DARK);
        colors.put(ActivityCategory.ENTERTAINMENT, ENTERTAINMENT_DARK);
        colors.put(ActivityCategory.PARKS_NATURE, PARKS_DARK);
        colors.put(ActivityCategory.HISTORIC, HISTORIC_DARK);
        colors.put(ActivityCategory.SPORTS_RECREATION, SPORTS_DARK);
        colors.put(ActivityCategory.ARTS_CULTURE, ARTS_DARK);
        return colors;
    }

    /**
     * Performs the c ar db or de r operation.
     * @return the result of the operation
     */
    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE, 1, true),
                BorderFactory.createEmptyBorder(CARD_PAD_Y, CARD_PAD_X, CARD_PAD_Y, CARD_PAD_X));
    }

    /**
     * Performs the s ty le ca rd operation.
     * @param component the c om po ne nt value
     */
    public static void styleCard(JComponent component) {
        component.setBackground(PANEL);
        component.setBorder(cardBorder());
    }

    /**
     * Performs the p ri ma ry bu tt on operation.
     * @param text the t ex t value
     * @return the result of the operation
     */
    public static JButton primaryButton(String text) {
        final JButton button = new JButton(text);
        button.putClientProperty("trippy.buttonRole", "primary");
        button.setFont(BODY.deriveFont(Font.BOLD));
        button.setForeground(Color.WHITE);
        button.setBackground(BLUE);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(PRIMARY_PAD_Y, PRIMARY_PAD_X, PRIMARY_PAD_Y, PRIMARY_PAD_X));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setUI(new RoundedButtonUI());
        return button;
    }

    /**
     * Performs the s ec on da ry bu tt on operation.
     * @param text the t ex t value
     * @return the result of the operation
     */
    public static JButton secondaryButton(String text) {
        final JButton button = new JButton(text);
        button.putClientProperty("trippy.buttonRole", "secondary");
        button.setFont(BODY);
        button.setForeground(NAVY);
        button.setBackground(PANEL);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setUI(new RoundedButtonUI());
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(FIELD_PAD_Y, FIELD_PAD_X, FIELD_PAD_Y, FIELD_PAD_X)));
        return button;
    }

    /**
     * A small status pill, e.g. {@code Locked} or {@code Moved}.
     *
     * <p>The text carries the meaning; the tint is decoration. That ordering matters — the
     * Preview previously wrote " [locked]" into an HTML label, which is readable but reads
     * as an afterthought rather than a state.</p>
      * @param text the t ex t value
      * @param surface the s ur fa ce value
      * @param ink the i nk value
      * @return the result of the operation
     */
    public static JLabel badge(String text, Color ink, Color surface) {
        final JLabel badge = new JLabel(text);
        badge.setFont(SMALL.deriveFont(Font.BOLD));
        badge.setForeground(ink);
        badge.setBackground(surface);
        badge.setOpaque(true);
        badge.setBorder(BorderFactory.createEmptyBorder(PILL_PAD_Y, PILL_PAD_X, PILL_PAD_Y, PILL_PAD_X));
        badge.getAccessibleContext().setAccessibleName(text);
        return badge;
    }

    /**
     * A section heading with a rule running to the right of it.
     * @param trailing the t ra il in g value
     * @param accent the a cc en t value
     * @param title the t it le value
     * @return the result of the operation
     */
    public static JPanel sectionHeader(String title, String trailing, Color accent) {
        final JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JLabel label = new JLabel(title);
        label.setFont(SMALL.deriveFont(Font.BOLD));
        label.setForeground(accent);
        header.add(label, BorderLayout.WEST);

        if (trailing != null && !trailing.isEmpty()) {
            final JLabel note = new JLabel(trailing);
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
      * @param value the v al ue value
      * @param caption the c ap ti on value
      * @return the result of the operation
     */
    public static JPanel metricCard(String value, String caption) {
        final JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(BLUE_SOFT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(214, 229, 250)),
                BorderFactory.createEmptyBorder(NOTICE_PAD_Y, NOTICE_PAD_X, NOTICE_PAD_Y, NOTICE_PAD_X)));

        final JLabel figure = new JLabel(value);
        figure.setFont(BODY.deriveFont(Font.BOLD));
        figure.setForeground(NAVY);
        figure.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(figure);

        final JLabel label = new JLabel(caption);
        label.setFont(SMALL);
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(label);

        card.getAccessibleContext().setAccessibleName(caption + ": " + value);
        return card;
    }

    /**
     * A caution strip, visually separate from both the figures and the reasoning.
     * @param messages the m es sa ge s value
     * @return the result of the operation
     */
    public static JPanel warningBand(java.util.List<String> messages) {
        final JPanel band = new JPanel();
        band.setLayout(new BoxLayout(band, BoxLayout.Y_AXIS));
        band.setBackground(WARNING_SOFT);
        band.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, NOTICE_STRIPE, 0, 0, WARNING),
                BorderFactory.createEmptyBorder(NOTICE_PAD_Y, NOTICE_INNER_X, NOTICE_PAD_Y, NOTICE_INNER_X)));
        band.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (String message : messages) {
            final JLabel line = new JLabel("<html>&#9888; " + message
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

    /**
     * Performs the p la ce ho ld er bu tt on operation.
     * @param text the t ex t value
     * @return the result of the operation
     */
    public static JButton placeholderButton(String text) {
        final JButton button = new JButton(text);
        button.setFont(SMALL);
        button.setEnabled(false);
        button.setToolTipText("Not wired for this milestone");
        return button;
    }
}
