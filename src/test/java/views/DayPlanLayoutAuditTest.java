package views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.WeatherWarning;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.WeatherOption;
import entity.valueobjects.WeatherSeverity;
import interface_adapter.controllers.AutoScheduleController;
import interface_adapter.controllers.AutoScheduleSettings;
import interface_adapter.controllers.TaskRunner;
import interface_adapter.presenters.AutoSchedulePresenter;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.PreviewMetricsView;
import interface_adapter.viewmodels.PreviewRowView;
import use_case.autoschedule.AutoScheduleAppliedOutputData;
import use_case.autoschedule.AutoScheduleApplyInputData;
import use_case.autoschedule.AutoScheduleInputBoundary;
import use_case.autoschedule.AutoScheduleInputData;
import use_case.autoschedule.ProposalEditInputData;
import use_case.autoschedule.ProposedEventData;

/**
 * Layout properties that can be proved without a person looking at the window.
 *
 * <p>Several of the panel's widths are now computed from {@code getWidth()} so that long text
 * wraps instead of being cut off. That is only correct if it is recomputed when the width
 * actually changes — a value read once, before the panel has bounds, would leave the wrap stuck
 * at its fallback for the life of the window.</p>
 */
class DayPlanLayoutAuditTest {

    private static final AutoScheduleInputBoundary INERT = new AutoScheduleInputBoundary() {
        @Override
        public void preview(AutoScheduleInputData inputData) {
        }

        @Override
        public void apply(AutoScheduleApplyInputData inputData) {
        }

        @Override
        public WeatherOption weatherOptionFor(String tripId) {
            return WeatherOption.available();
        }

        @Override
        public void removeFromProposal(ProposalEditInputData inputData) {
        }
    };

    private static final String LONG_CONFLICT =
            "40/60 Food Shop Take Away is closed on Sundays, so it cannot be scheduled on this "
            + "date at any time. Remove it from the day, or choose a date it is open. "
            + "Your Day Plan was not changed.";

    private static ScheduledEvent event(String id, int hour) {
        final Activity activity = new Activity(id, id, ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, id), 4.5, 60, LocalTime.of(8, 0),
                LocalTime.of(21, 0), IndoorOutdoorType.INDOOR, "none");
        return new ScheduledEvent(id, activity, LocalTime.of(hour, 0),
                LocalTime.of(hour + 1, 0), EventType.ACTIVITY, "");
    }

    private static ScheduledEvent eventNamed(String id, String name, LocalTime start,
                                             int durationMinutes) {
        final Activity activity = new Activity(id, name, ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, id), 4.5, durationMinutes, LocalTime.of(8, 0),
                LocalTime.of(21, 0), IndoorOutdoorType.INDOOR, "none");
        return new ScheduledEvent(id, activity, start, start.plusMinutes(durationMinutes),
                EventType.ACTIVITY, "");
    }

    private static DayPlanState conflictState() {
        return new DayPlanState("trip-1", Collections.singletonList(event("a", 9)),
                LONG_CONFLICT, true, Collections.emptyList(), AutoScheduleStatus.CONFLICT,
                Collections.emptyList(), null, Collections.emptyList(), "", true, true, "", "",
                Collections.emptySet());
    }

    private static DayPlanState previewState() {
        final List<PreviewRowView> rows = new ArrayList<>();
        rows.add(new PreviewRowView("a", "A Very Long Activity Name That Could Easily Overflow",
                PreviewRowView.Kind.ACTIVITY, LocalTime.of(11, 0), LocalTime.of(12, 0),
                false, true, "moved", Collections.singletonList("moved")));
        rows.add(new PreviewRowView("travel-b", "Travel to Somewhere",
                PreviewRowView.Kind.TRAVEL, LocalTime.of(12, 0), LocalTime.of(12, 4),
                false, false, "", Collections.emptyList()));
        rows.add(new PreviewRowView("b", "Another Place",
                PreviewRowView.Kind.ACTIVITY, LocalTime.of(12, 4), LocalTime.of(13, 4),
                false, true, "moved", Collections.singletonList("moved")));
        return new DayPlanState("trip-1",
                List.of(event("a", 9), event("b", 12)),
                "Proposed schedule: 3 of 4 activities moved. Nothing changes until you "
                        + "choose Apply.",
                false, Collections.emptyList(), AutoScheduleStatus.PREVIEW, rows,
                new PreviewMetricsView(30, 20, 90, 10, 3, 4, 120),
                Collections.singletonList("Travel times may include estimates."),
                "Arranged for less travel", true, true, "", "fingerprint",
                Collections.emptySet());
    }

    private static DayPlanState cardStressState(int travelMinutes) {
        return cardStressState(travelMinutes, 60);
    }

    private static DayPlanState cardStressState(int travelMinutes, int activityMinutes) {
        final String longName = "A Very Long Independent Fine Food Market and Kitchen";
        final List<ScheduledEvent> saved = List.of(
                eventNamed("a", "First Museum", LocalTime.of(9, 0), 60),
                eventNamed("b", longName, LocalTime.of(10, 0).plusMinutes(travelMinutes),
                        activityMinutes));
        final List<PreviewRowView> rows = List.of(
                new PreviewRowView("a", "First Museum", PreviewRowView.Kind.ACTIVITY,
                        LocalTime.of(9, 0), LocalTime.of(10, 0), true, false,
                        "you locked this time", List.of("you locked this time")),
                new PreviewRowView("travel-b", "Travel to " + longName,
                        PreviewRowView.Kind.TRAVEL, LocalTime.of(10, 0),
                        LocalTime.of(10, 0).plusMinutes(travelMinutes), false, false,
                        "", Collections.emptyList()),
                new PreviewRowView("b", longName, PreviewRowView.Kind.ACTIVITY,
                        LocalTime.of(10, 0).plusMinutes(travelMinutes),
                        LocalTime.of(10, 0).plusMinutes(travelMinutes + activityMinutes),
                        false, true,
                        "moved after unavailable time", List.of("moved after unavailable time")));
        final WeatherWarning detail = new WeatherWarning(new Location(43.65, -79.38, "b"),
                LocalTime.of(10, 0), "Rain", WeatherSeverity.MEDIUM,
                "Bring a compact umbrella");
        return new DayPlanState("trip-1", saved, "Proposed schedule", false,
                Collections.singletonList(detail), AutoScheduleStatus.PREVIEW, rows,
                new PreviewMetricsView(20, travelMinutes, 60, 0, 1, 2, 60),
                Collections.emptyList(), "Arranged around unavailable time", true, true,
                "", "fingerprint", Collections.singleton("a"));
    }

    private static DayPlanPanel panelFor(DayPlanViewModel viewModel) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "these components need a display");
        final DayPlanPanel[] built = new DayPlanPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            built[0] = new DayPlanPanel(viewModel,
                    new AutoScheduleController(INERT, viewModel, TaskRunner.immediate()));
        });
        return built[0];
    }

    private static JFrame host(DayPlanPanel panel, int width) throws Exception {
        final JFrame frame = new JFrame();
        SwingUtilities.invokeAndWait(() -> {
            frame.setUndecorated(true);
            frame.getContentPane().add(panel);
            frame.setSize(width, 900);
            frame.addNotify();
            frame.validate();
        });
        drain();
        return frame;
    }

    private static void resize(DayPlanPanel panel, JFrame frame, int width) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame.setSize(width, 900);
            frame.validate();
        });
        drain();
    }

    private static void drain() throws Exception {
        for (int i = 0; i < 3; i++) {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }

    /** Writes optional human-review evidence only when the audit command requests it. */
    private static void writeEvidence(String name, BufferedImage image) throws Exception {
        final String directory = System.getProperty("finalGateEvidenceDir", "").trim();
        if (directory.isEmpty()) {
            return;
        }
        final File folder = new File(directory);
        assertTrue(folder.isDirectory() || folder.mkdirs(),
                "could not create evidence directory " + folder);
        assertTrue(ImageIO.write(image, "png", new File(folder, name)),
                "no PNG writer was available");
    }

    private static List<Component> all(Component root) {
        final List<Component> found = new ArrayList<>();
        collect(root, found);
        return found;
    }

    private static void collect(Component component, List<Component> into) {
        into.add(component);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, into);
            }
        }
    }

    /**
     * Everything the timeline draws has to be inside what the timeline can be scrolled to.
     *
     * <p>Reported from the running application: the proposed day could not be scrolled far
     * enough to see the rest of it. The window was being clamped to the trip's own closing
     * hour while the cards were positioned from their real times, so a proposal running past
     * that hour was laid out at a y below the component's own bottom edge — drawn, correctly
     * placed, and unreachable by any amount of scrolling.</p>
     */
    @Test
    void everyProposedCardSitsInsideTheScrollableTimeline() throws Exception {
        final List<PreviewRowView> rows = new ArrayList<>();
        final List<ScheduledEvent> saved = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final int hour = Math.min(9 + i * 2, 22);
            saved.add(event("e" + i, hour));
            rows.add(new PreviewRowView("e" + i, "Activity " + i,
                    PreviewRowView.Kind.ACTIVITY, LocalTime.of(hour, 0),
                    LocalTime.of(hour + 1, 0), false, true, "moved",
                    Collections.singletonList("moved")));
        }
        final DayPlanState late = new DayPlanState("trip-1", saved, "Proposed schedule", false,
                Collections.emptyList(), AutoScheduleStatus.PREVIEW, rows,
                new PreviewMetricsView(40, 30, 100, 20, 8, 8, 120),
                Collections.emptyList(), "Arranged for less travel", true, true, "",
                "fingerprint", Collections.emptySet());

        final DayPlanViewModel viewModel = new DayPlanViewModel(late);
        final DayPlanPanel panel = panelFor(viewModel);
        final JFrame frame = host(panel, 1180);

        final int[] measured = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            final Container timeline = timelineIn(panel);
            assertNotNull(timeline, "the schedule is drawn into a timeline");
            timeline.setSize(600, timeline.getPreferredSize().height);
            timeline.doLayout();
            int lowest = 0;
            for (Component card : timeline.getComponents()) {
                lowest = Math.max(lowest, card.getY() + card.getHeight());
            }
            measured[0] = lowest;
            measured[1] = timeline.getPreferredSize().height;
        });
        frame.dispose();

        assertTrue(measured[0] > 0, "the fixture should actually draw cards");
        assertTrue(measured[0] <= measured[1],
                "a card drawn at " + measured[0] + "px cannot be reached in a timeline only "
                        + measured[1] + "px tall");
    }

    /** The wrap width baked into an html label, or -1 when the label does not set one. */
    private static int wrapWidthOf(Component root, String fragment) {
        for (Component component : all(root)) {
            if (!(component instanceof JLabel)) {
                continue;
            }
            final String text = ((JLabel) component).getText();
            if (text == null || !text.contains(fragment) || !text.contains("width:")) {
                continue;
            }
            final int start = text.indexOf("width:") + "width:".length();
            final int end = text.indexOf("px", start);
            return Integer.parseInt(text.substring(start, end));
        }
        return -1;
    }

    /**
     * The reflow concern: a width read before the panel had bounds must not be kept forever.
     */
    @Test
    void theConflictTextReflowsWhenThePanelIsActuallyResized() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(conflictState());
        final DayPlanPanel panel = panelFor(viewModel);

        final int beforeBounds = wrapWidthOf(panel, "closed on Sundays");
        assertTrue(beforeBounds > 0,
                "even with no bounds the message must be given a width to wrap at");

        final JFrame frame = host(panel, 1200);
        final int wide = wrapWidthOf(panel, "closed on Sundays");
        resize(panel, frame, 700);
        final int narrow = wrapWidthOf(panel, "closed on Sundays");
        frame.dispose();

        assertTrue(wide > narrow,
                "the message must reflow with the panel rather than stay at the fallback "
                        + "(wide=" + wide + ", narrow=" + narrow + ")");
        assertTrue(narrow > 0, "and never collapse to nothing");
    }

    @Test
    void theStatusTextReflowsToo() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
        final DayPlanPanel panel = panelFor(viewModel);
        final JFrame frame = host(panel, 1200);

        final int wide = wrapWidthOf(panel, "activities moved");
        resize(panel, frame, 700);
        final int narrow = wrapWidthOf(panel, "activities moved");
        frame.dispose();

        assertTrue(wide > narrow, "status wraps with the panel (wide=" + wide
                + ", narrow=" + narrow + ")");
    }

    /** No ellipsis anywhere: a cut sentence hides the only number that mattered. */
    @Test
    void neitherTheConflictNorTheStatusIsEverTruncatedWithAnEllipsis() throws Exception {
        for (DayPlanState state : List.of(conflictState(), previewState())) {
            final DayPlanViewModel viewModel = new DayPlanViewModel(state);
            final DayPlanPanel panel = panelFor(viewModel);
            final JFrame frame = host(panel, 760);

            for (Component component : all(panel)) {
                if (component instanceof JLabel) {
                    final String text = ((JLabel) component).getText();
                    if (text != null && text.contains("activities mo…")) {
                        frame.dispose();
                        throw new AssertionError("status was truncated: " + text);
                    }
                }
            }
            frame.dispose();
        }
    }

    /** The whole conflict sentence has to be present, however narrow the window gets. */
    @Test
    void theWholeConflictSentenceSurvivesANarrowWindow() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(conflictState());
        final DayPlanPanel panel = panelFor(viewModel);
        final JFrame frame = host(panel, 620);

        final StringBuilder text = new StringBuilder();
        for (Component component : all(panel)) {
            if (component instanceof JLabel && ((JLabel) component).getText() != null) {
                text.append(((JLabel) component).getText()).append(' ');
            }
        }
        frame.dispose();

        assertTrue(text.toString().contains("closed on Sundays"), text.toString());
        assertTrue(text.toString().contains("Your Day Plan was not changed"),
                "the reassurance is the part a worried traveller needs: " + text);
    }

    /** OK must stay reachable no matter how long the message is. */
    @Test
    void theDismissButtonStaysVisibleBesideALongConflict() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(conflictState());
        final DayPlanPanel panel = panelFor(viewModel);
        final JFrame frame = host(panel, 700);

        AbstractButton ok = null;
        for (Component component : all(panel)) {
            if (component instanceof AbstractButton
                    && "OK".equals(((AbstractButton) component).getText())) {
                ok = (AbstractButton) component;
            }
        }
        assertNotNull(ok, "a message the traveller cannot dismiss is a trap");
        // The bar is built during a render triggered by the resize event, so its children get
        // their bounds on the layout pass after that; validate once more before measuring.
        final AbstractButton measured = ok;
        SwingUtilities.invokeAndWait(frame::validate);
        drain();
        assertTrue(measured.getWidth() > 0 && measured.getHeight() > 0,
                "OK must be laid out and clickable, not a zero-sized component");
        assertTrue(measured.getX() + measured.getWidth() <= panel.getWidth(),
                "and it must sit inside the panel rather than off its right edge");
        frame.dispose();
    }

    @Test
    void conflictTextAndDismissButtonStayInsideTheBarAtANarrowWidth() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(conflictState());
        final DayPlanPanel panel = panelFor(viewModel);
        final JFrame frame = host(panel, 400);
        SwingUtilities.invokeAndWait(frame::validate);
        drain();
        SwingUtilities.invokeAndWait(frame::validate);
        drain();

        JPanel bar = null;
        JLabel detail = null;
        AbstractButton ok = null;
        for (Component component : all(panel)) {
            final String accessible = component.getAccessibleContext() == null ? null
                    : component.getAccessibleContext().getAccessibleName();
            if (component instanceof JPanel && accessible != null
                    && accessible.startsWith("Autoschedule could not run:")) {
                bar = (JPanel) component;
            }
            if (component instanceof AbstractButton
                    && "OK".equals(((AbstractButton) component).getText())) {
                ok = (AbstractButton) component;
            }
        }
        assertNotNull(bar);
        for (Component component : all(bar)) {
            if (component instanceof JLabel && ((JLabel) component).getText() != null
                    && ((JLabel) component).getText().contains("closed on Sundays")) {
                detail = (JLabel) component;
            }
        }
        assertNotNull(detail);
        assertNotNull(ok);

        final java.awt.Rectangle detailBounds = SwingUtilities.convertRectangle(
                detail.getParent(), detail.getBounds(), bar);
        final java.awt.Rectangle okBounds = SwingUtilities.convertRectangle(
                ok.getParent(), ok.getBounds(), bar);
        assertTrue(detailBounds.x >= 0 && detailBounds.y >= 0
                        && detailBounds.x + detailBounds.width <= bar.getWidth()
                        && detailBounds.y + detailBounds.height <= bar.getHeight(),
                "detail paints outside the conflict bar: detail=" + detailBounds
                        + " bar=" + bar.getBounds() + " preferred=" + detail.getPreferredSize());
        assertTrue(detail.getHeight() >= detail.getPreferredSize().height,
                "wrapped lines are vertically clipped: assigned=" + detail.getBounds()
                        + " preferred=" + detail.getPreferredSize());
        final int declaredWrapWidth = wrapWidthOf(bar, "closed on Sundays");
        assertTrue(declaredWrapWidth > 0 && declaredWrapWidth <= detail.getWidth(),
                "the HTML wrap width must fit the real text region: declared="
                        + declaredWrapWidth + " assigned=" + detail.getBounds());
        assertTrue(okBounds.x >= detailBounds.x + detailBounds.width
                        && okBounds.x + okBounds.width <= bar.getWidth(),
                "OK must remain to the right without overlapping text: detail=" + detailBounds
                        + " ok=" + okBounds + " bar=" + bar.getBounds());
        frame.dispose();
    }

    @Test
    void dismissingAConflictClearsOnlyTheNoticeAndKeepsRetrySettings() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(conflictState()
                .withLocks(Collections.singleton("a")));
        final AutoScheduleController controller = new AutoScheduleController(
                INERT, viewModel, TaskRunner.immediate());
        final AutoScheduleSettings remembered = new AutoScheduleSettings(
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                Collections.singletonList(new AutoScheduleSettings.Window(
                        LocalTime.of(12, 0), LocalTime.of(13, 0))), true, true);
        // Preview owns the memory update. Restore the returned conflict as the Presenter would.
        controller.preview(remembered);
        final DayPlanState conflict = conflictState().withLocks(Collections.singleton("a"));
        viewModel.setState(conflict);

        final DayPlanPanel panel = new DayPlanPanel(viewModel, controller);
        AbstractButton ok = null;
        for (Component component : all(panel)) {
            if (component instanceof AbstractButton
                    && "OK".equals(((AbstractButton) component).getText())) {
                ok = (AbstractButton) component;
            }
        }
        assertNotNull(ok);
        final AbstractButton click = ok;
        SwingUtilities.invokeAndWait(click::doClick);
        drain();

        assertEquals(AutoScheduleStatus.IDLE, viewModel.getState().getStatus());
        assertEquals("", viewModel.getState().getMessage());
        assertEquals(conflict.getEvents(), viewModel.getState().getEvents(),
                "dismissing cannot modify the saved plan");
        assertEquals(conflict.getLockedEventIds(), viewModel.getState().getLockedEventIds());
        assertSame(remembered, controller.rememberedSettings(),
                "the next retry must reopen with the same settings");
    }

    /** Apply and Cancel are the point of a Preview; they may never be pushed off screen. */
    @Test
    void applyAndCancelAreVisibleAtEveryWidth() throws Exception {
        for (int width : new int[]{620, 760, 900, 1200, 1600}) {
            final DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
            final DayPlanPanel panel = panelFor(viewModel);
            final JFrame frame = host(panel, width);

            boolean apply = false;
            boolean cancel = false;
            for (Component component : all(panel)) {
                if (component instanceof AbstractButton) {
                    final String label = ((AbstractButton) component).getText();
                    apply |= "Apply".equals(label) && component.isVisible();
                    cancel |= "Cancel".equals(label) && component.isVisible();
                }
            }
            frame.dispose();
            assertTrue(apply, "Apply missing at " + width + "px");
            assertTrue(cancel, "Cancel missing at " + width + "px");
        }
    }

    /** Repeated Previews must not leave two of anything behind. */
    @Test
    void openingAPreviewRepeatedlyDoesNotAccumulatePanels() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
        final DayPlanPanel panel = panelFor(viewModel);
        final JFrame frame = host(panel, 1200);

        final int first = countImprovementPanels(panel);
        for (int i = 0; i < 4; i++) {
            SwingUtilities.invokeAndWait(() -> viewModel.setState(previewState()));
            drain();
        }
        final int afterFive = countImprovementPanels(panel);
        frame.dispose();

        assertEquals(first, afterFive,
                "each render must replace the reasoning column, not add another");
        assertEquals(1, afterFive, "and there should be exactly one of it");
    }

    private static int countImprovementPanels(Component root) {
        int count = 0;
        for (Component component : all(root)) {
            if (component instanceof ScheduleImprovementsPanel) {
                count++;
            }
        }
        return count;
    }

    private static Container timelineIn(Component root) {
        for (Component component : all(root)) {
            if ("Day schedule timeline".equals(component.getName())) {
                return (Container) component;
            }
        }
        return null;
    }

    /**
     * A timeline can temporarily be shorter than its preferred height while Swing is
     * relaying out a resized window. Hour labels outside that temporary viewport must be
     * clipped, not all moved onto the final visible baseline.
     */
    @Test
    void outOfBoundsHourLabelsDoNotCollapseOntoTheBottomEdge() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                "trip-1", Collections.<ScheduledEvent>emptyList(), "", false));
        final DayPlanPanel panel = panelFor(viewModel);
        final JFrame frame = host(panel, 1200);
        final Container timeline = timelineIn(panel);
        assertNotNull(timeline);

        final BufferedImage image = new BufferedImage(500, 360, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> {
            timeline.setSize(500, 360);
            timeline.doLayout();
            timeline.paint(image.createGraphics());
        });
        writeEvidence("timeline-hour-labels-fixed.png", image);

        final int background = SwingTheme.PANEL.getRGB() & 0x00ffffff;
        int bottomLabelInk = 0;
        // The time-label gutter only. The one-pixel panel border is outside this box.
        for (int y = 336; y < 359; y++) {
            for (int x = 6; x < 66; x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != background) {
                    bottomLabelInk++;
                }
            }
        }
        frame.dispose();

        assertEquals(0, bottomLabelInk,
                "off-screen hour labels were stacked onto the bottom edge (ink="
                        + bottomLabelInk + ")");
    }

    private static JPanel cardContaining(Container timeline, String fragment) {
        for (Component component : timeline.getComponents()) {
            if (!(component instanceof JPanel)) {
                continue;
            }
            boolean containsText = false;
            boolean hasActivityActions = false;
            for (Component child : all(component)) {
                if (child instanceof JLabel && ((JLabel) child).getText() != null
                        && ((JLabel) child).getText().contains(fragment)) {
                    containsText = true;
                }
                if (child instanceof AbstractButton) {
                    hasActivityActions = true;
                }
            }
            if (containsText && hasActivityActions) {
                return (JPanel) component;
            }
        }
        return null;
    }

    private static JPanel travelCardContaining(Container timeline, String fragment) {
        for (Component component : timeline.getComponents()) {
            if (!(component instanceof JPanel)) {
                continue;
            }
            boolean containsText = false;
            boolean hasActivityActions = false;
            for (Component child : all(component)) {
                if (child instanceof JLabel && ((JLabel) child).getText() != null
                        && ((JLabel) child).getText().contains(fragment)) {
                    containsText = true;
                }
                hasActivityActions |= child instanceof AbstractButton;
            }
            if (containsText && !hasActivityActions) {
                return (JPanel) component;
            }
        }
        return null;
    }

    private static void layoutTree(Component component) {
        if (component instanceof Container) {
            ((Container) component).doLayout();
            for (Component child : ((Container) component).getComponents()) {
                layoutTree(child);
            }
        }
    }

    private static void assertVisibleContentsStayInside(JPanel card) {
        for (Component component : all(card)) {
            if (component == card || !component.isVisible()) {
                continue;
            }
            final java.awt.Rectangle bounds = SwingUtilities.convertRectangle(
                    component.getParent(), component.getBounds(), card);
            assertTrue(bounds.x >= 0 && bounds.y >= 0
                            && bounds.x + bounds.width <= card.getWidth()
                            && bounds.y + bounds.height <= card.getHeight(),
                    "content paints outside its card: child=" + component.getClass().getName()
                            + " text=" + (component instanceof JLabel
                            ? ((JLabel) component).getText() : "")
                            + " childBounds=" + bounds + " card=" + card.getBounds()
                            + " preferred=" + card.getPreferredSize());
        }
    }

    @Test
    void aLongPreviewCardKeepsItsFullTimeAndAllContentInsideItsBounds() throws Exception {
        final String longName = "A Very Long Independent Fine Food Market and Kitchen";
        final DayPlanPanel panel = panelFor(new DayPlanViewModel(cardStressState(10)));
        final JFrame frame = host(panel, 620);
        SwingUtilities.invokeAndWait(frame::validate);
        drain();

        final Container timeline = timelineIn(panel);
        assertNotNull(timeline);
        SwingUtilities.invokeAndWait(() -> {
            timeline.setSize(500, timeline.getPreferredSize().height);
            timeline.doLayout();
        });
        final JPanel card = cardContaining(timeline, longName);
        assertNotNull(card);
        SwingUtilities.invokeAndWait(card::doLayout);

        assertTrue(card.getHeight() >= card.getPreferredSize().height,
                "assigned card is shorter than its contents: assigned=" + card.getBounds()
                        + " preferred=" + card.getPreferredSize());

        JLabel title = null;
        JLabel time = null;
        int visibleControls = 0;
        for (Component component : all(card)) {
            if (component instanceof JLabel && ((JLabel) component).getText() != null) {
                final String text = ((JLabel) component).getText();
                if (text.contains(longName)) {
                    title = (JLabel) component;
                }
                if ("10:10 AM – 11:10 AM".equals(text)) {
                    time = (JLabel) component;
                }
            }
            if (component instanceof AbstractButton && component.isVisible()) {
                visibleControls++;
            }
        }
        assertNotNull(title);
        assertTrue(title.getFontMetrics(title.getFont()).stringWidth(title.getText())
                        <= title.getWidth()
                        || (title.getToolTipText() != null
                        && title.getToolTipText().contains(longName)),
                "a clipped long title must retain its full value in a tooltip");
        assertNotNull(time, "the full time range needs its own non-truncated label");
        assertTrue(time.getFontMetrics(time.getFont()).stringWidth(time.getText())
                <= time.getWidth(), "the end time must be visible in full");
        assertEquals(3, visibleControls, "lock, Edit, and Remove must remain visible");
        frame.dispose();
    }

    @Test
    void shortTravelConnectorsStayReadableWithoutCoveringTheirDestination() throws Exception {
        final String longName = "A Very Long Independent Fine Food Market and Kitchen";
        for (int travelMinutes : new int[]{2, 10, 20}) {
            final DayPlanPanel panel = panelFor(new DayPlanViewModel(cardStressState(travelMinutes)));
            final JFrame frame = host(panel, 620);
            final Container timeline = timelineIn(panel);
            assertNotNull(timeline);
            SwingUtilities.invokeAndWait(() -> {
                timeline.setSize(500, timeline.getPreferredSize().height);
                layoutTree(timeline);
            });

            final JPanel travel = travelCardContaining(timeline, "Travel to " + longName);
            final JPanel destination = cardContaining(timeline, longName);
            assertNotNull(travel, "missing " + travelMinutes + "-minute travel connector");
            assertNotNull(destination);
            final int travelBottom = travel.getY() + travel.getHeight();
            for (Component child : all(destination)) {
                if (child == destination || !child.isVisible()
                        || !(child instanceof JLabel || child instanceof AbstractButton)) {
                    continue;
                }
                final java.awt.Rectangle inTimeline = SwingUtilities.convertRectangle(
                        child.getParent(), child.getBounds(), timeline);
                assertTrue(inTimeline.y >= travelBottom,
                        travelMinutes + "-minute connector covers destination content: travel="
                                + travel.getBounds() + " child=" + inTimeline);
            }
            assertVisibleContentsStayInside(travel);
            assertVisibleContentsStayInside(destination);
            frame.dispose();
        }
    }

    @Test
    void appliedTravelRemainsAVisibleNormalDayPlanRow() throws Exception {
        final ScheduledEvent first = eventNamed("a", "First Museum", LocalTime.of(9, 0), 60);
        final ScheduledEvent second = eventNamed("b", "Second Museum", LocalTime.of(12, 0), 60);
        final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                "trip-1", List.of(first, second), "", false));
        new AutoSchedulePresenter(viewModel).presentApplied(new AutoScheduleAppliedOutputData(
                "trip-1", List.of(
                    new ProposedEventData("a", "a", "First Museum",
                        ProposedEventData.Kind.ACTIVITY, LocalTime.of(9, 0),
                        LocalTime.of(10, 0), false, false),
                    new ProposedEventData("travel-b", "", "Travel to Second Museum",
                        ProposedEventData.Kind.TRAVEL, LocalTime.of(10, 0),
                        LocalTime.of(10, 20), false, false),
                    new ProposedEventData("b", "b", "Second Museum",
                        ProposedEventData.Kind.ACTIVITY, LocalTime.of(10, 20),
                        LocalTime.of(11, 20), false, true)), "saved-fingerprint"));

        final DayPlanPanel panel = panelFor(viewModel);
        final JFrame frame = host(panel, 760);
        final Container timeline = timelineIn(panel);
        assertNotNull(timeline);
        SwingUtilities.invokeAndWait(() -> {
            timeline.setSize(620, timeline.getPreferredSize().height);
            layoutTree(timeline);
        });

        assertEquals(AutoScheduleStatus.APPLIED, viewModel.getState().getStatus());
        assertEquals(EventType.TRAVEL, viewModel.getState().getEvents().get(1).getEventType(),
                "the Presenter must preserve the saved travel row");
        final JPanel travel = travelCardContaining(timeline, "Travel to Second Museum");
        assertNotNull(travel, "the normal Day Plan renderer must not filter applied travel");
        assertTrue(travel.isVisible() && travel.getWidth() > 0 && travel.getHeight() > 0,
                "the applied travel row must receive visible bounds: "
                        + (travel == null ? "missing" : travel.getBounds()));
        assertEquals(SwingTheme.TRAVEL_SURFACE, travel.getBackground(),
                "an applied connector must not disappear into the normal timeline surface; "
                        + "bounds=" + travel.getBounds());
        assertVisibleContentsStayInside(travel);
        int paintedLabels = 0;
        final StringBuilder labelBounds = new StringBuilder();
        for (Component child : all(travel)) {
            if (child instanceof JLabel) {
                labelBounds.append(((JLabel) child).getText()).append('=')
                        .append(child.getBounds()).append(';');
                if (child.isVisible() && child.getWidth() > 0 && child.getHeight() > 0) {
                    paintedLabels++;
                }
            }
        }
        assertEquals(2, paintedLabels,
                "the applied connector must paint both its route label and full time range: "
                        + labelBounds);

        final BufferedImage evidence = new BufferedImage(620, 360, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> timeline.paint(evidence.createGraphics()));
        writeEvidence("applied-travel-row-fixed.png", evidence);
        frame.dispose();
    }

    @Test
    void shortActivityReflowsAtNarrowAndNormalWidthsWithAllActionsPresent() throws Exception {
        final String longName = "A Very Long Independent Fine Food Market and Kitchen";
        final DayPlanPanel panel = panelFor(new DayPlanViewModel(cardStressState(10, 30)));
        final JFrame frame = host(panel, 620);
        final Container timeline = timelineIn(panel);
        assertNotNull(timeline);

        for (int width : new int[]{360, 720, 430}) {
            SwingUtilities.invokeAndWait(() -> {
                timeline.setSize(width, timeline.getPreferredSize().height);
                layoutTree(timeline);
            });
            final JPanel card = cardContaining(timeline, longName);
            assertNotNull(card);
            assertTrue(card.getHeight() >= card.getPreferredSize().height,
                    "short activity clips after resize to " + width + ": assigned="
                            + card.getBounds() + " preferred=" + card.getPreferredSize());
            assertVisibleContentsStayInside(card);

            int controls = 0;
            AbstractButton edit = null;
            for (Component child : all(card)) {
                if (child instanceof AbstractButton && child.isVisible()) {
                    controls++;
                    final String accessible = child.getAccessibleContext().getAccessibleName();
                    if (accessible != null && accessible.startsWith("Edit ")) {
                        edit = (AbstractButton) child;
                    }
                }
            }
            assertEquals(3, controls, "all actions remain at " + width + "px");
            assertNotNull(edit);
            assertFalse(edit.isEnabled(), "Edit is deliberately disabled in Preview");
        }
        frame.dispose();
    }

    /** Cancelling must take the whole Preview presentation away, not just the rows. */
    @Test
    void cancellingLeavesNoReasoningBehind() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
        final DayPlanPanel panel = panelFor(viewModel);
        final JFrame frame = host(panel, 1200);
        assertEquals(1, countImprovementPanels(panel), "precondition");

        SwingUtilities.invokeAndWait(() -> {
            viewModel.setState(viewModel.getState().clearedPreview("Cancelled."));
        });
        drain();

        assertEquals(0, countImprovementPanels(panel),
                "the reasoning column belongs to the proposal and goes with it");
        boolean apply = false;
        for (Component component : all(panel)) {
            if (component instanceof AbstractButton
                    && "Apply".equals(((AbstractButton) component).getText())) {
                apply = component.isVisible();
            }
        }
        assertFalse(apply, "and there is nothing left to apply");
        frame.dispose();
    }
}
