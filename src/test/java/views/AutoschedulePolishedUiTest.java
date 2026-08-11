package views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
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
import interface_adapter.controllers.TaskRunner;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.ImprovementView;
import interface_adapter.viewmodels.PreviewMetricsView;
import interface_adapter.viewmodels.PreviewRowView;
import use_case.autoschedule.AutoScheduleApplyInputData;
import use_case.autoschedule.AutoScheduleInputBoundary;
import use_case.autoschedule.AutoScheduleInputData;

/**
 * The polished Day Plan, checked through the components it actually builds.
 *
 * <p>These assert behaviour a traveller can observe — a padlock that shows which state it is
 * in, a control that can be reached from the keyboard, times on a 12-hour clock, travel that
 * does not masquerade as an activity — rather than pixel positions, which would break on
 * every future tweak without protecting anything.</p>
 */
class AutoschedulePolishedUiTest {

    /** Records what the panel asked the use case to do, without scheduling anything. */
    private static final class RecordingUseCase implements AutoScheduleInputBoundary {
        @Override
        public void removeFromProposal(use_case.autoschedule.ProposalEditInputData inputData) {
            removedFromProposal.add(inputData == null ? "" : inputData.getRemoveEventId());
        }

        final java.util.List<String> removedFromProposal = new java.util.ArrayList<>();

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
    }

    private static Activity activity(String id) {
        return new Activity(id, id, ActivityCategory.ATTRACTION,
                new Location(43.65, -79.38, id), 4.5, 60,
                LocalTime.of(9, 0), LocalTime.of(20, 0), IndoorOutdoorType.INDOOR, "Low");
    }

    private static ScheduledEvent event(String id, int startHour) {
        return new ScheduledEvent(id, activity(id), LocalTime.of(startHour, 0),
                LocalTime.of(startHour + 1, 0), EventType.ACTIVITY, "");
    }

    private static DayPlanState planWith(List<ScheduledEvent> events, java.util.Set<String> locks,
                                         List<WeatherWarning> weather) {
        return new DayPlanState("trip-1", events, "", false, weather,
                AutoScheduleStatus.IDLE, Collections.emptyList(), null,
                Collections.emptyList(), "", true, true, "", "", locks);
    }

    private static DayPlanPanel panelFor(DayPlanViewModel viewModel) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "these components need a display");
        final DayPlanPanel[] built = new DayPlanPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            built[0] = new DayPlanPanel(viewModel,
                    new AutoScheduleController(new RecordingUseCase(), viewModel,
                            TaskRunner.immediate()));
        });
        return built[0];
    }

    // --- component search helpers -----------------------------------------------------

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

    private static List<JToggleButton> lockToggles(Component root) {
        final List<JToggleButton> toggles = new ArrayList<>();
        for (Component component : all(root)) {
            if (component instanceof JToggleButton) {
                final String name = component.getAccessibleContext().getAccessibleName();
                if (name != null && (name.startsWith("Lock ") || name.startsWith("Unlock "))) {
                    toggles.add((JToggleButton) component);
                }
            }
        }
        return toggles;
    }

    private static String allText(Component root) {
        final StringBuilder text = new StringBuilder();
        for (Component component : all(root)) {
            if (component instanceof JLabel) {
                text.append(((JLabel) component).getText()).append(' ');
            }
            else if (component instanceof AbstractButton) {
                text.append(((AbstractButton) component).getText()).append(' ');
            }
        }
        return text.toString();
    }

    private static AbstractButton buttonNamed(Component root, String text) {
        for (Component component : all(root)) {
            if (component instanceof AbstractButton
                    && text.equals(((AbstractButton) component).getText())) {
                return (AbstractButton) component;
            }
        }
        return null;
    }

    // --- 1. times read as a 12-hour clock ---------------------------------------------

    @Test
    void dayPlanTimesUseATwelveHourClock() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Arrays.asList(event("museum", 9), event("market", 15)),
                Collections.emptySet(), Collections.emptyList()));

        final String text = allText(panelFor(viewModel));

        assertTrue(text.contains("9:00 AM – 10:00 AM"), "morning row: " + text);
        assertTrue(text.contains("3:00 PM – 4:00 PM"), "afternoon row: " + text);
        assertFalse(text.contains("15:00"), "no 24-hour time should survive: " + text);
    }

    @Test
    void weatherTimestampsAlsoUseATwelveHourClock() throws Exception {
        final WeatherWarning noon = new WeatherWarning(new Location(43.65, -79.38, "Toronto"),
                LocalTime.of(13, 0), "Rain", WeatherSeverity.MEDIUM, "65% precipitation");
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 13)),
                Collections.emptySet(), Collections.singletonList(noon)));

        final String text = allText(panelFor(viewModel));

        assertTrue(text.contains("1:00 PM · Rain"), "weather line: " + text);
    }

    // --- 2. the lock control is visible, distinguishable and reachable ----------------

    @Test
    void everyActivityCarriesALockToggleShowingItsState() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Arrays.asList(event("museum", 9), event("market", 15)),
                new LinkedHashSet<>(Collections.singletonList("museum")),
                Collections.emptyList()));

        final List<JToggleButton> toggles = lockToggles(panelFor(viewModel));

        assertEquals(2, toggles.size(), "one lock control per activity");
        final JToggleButton locked = toggles.get(0);
        final JToggleButton unlocked = toggles.get(1);

        assertTrue(locked.isSelected(), "the pinned activity's control reads as locked");
        assertFalse(unlocked.isSelected());
        // The two states are told apart by the icon that is drawn, not only by colour.
        assertNotNull(locked.getIcon());
        assertNotNull(unlocked.getIcon());
    }

    @Test
    void theLockControlNamesTheActivityAndWhatPressingItWillDo() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Arrays.asList(event("museum", 9), event("market", 15)),
                new LinkedHashSet<>(Collections.singletonList("museum")),
                Collections.emptyList()));

        final List<JToggleButton> toggles = lockToggles(panelFor(viewModel));

        assertEquals("Unlock museum",
                toggles.get(0).getAccessibleContext().getAccessibleName(),
                "an already-pinned activity offers to unlock");
        assertEquals("Lock market at 3:00 PM",
                toggles.get(1).getAccessibleContext().getAccessibleName(),
                "an unpinned one offers to lock, and says at what time");
        assertNotNull(toggles.get(0).getToolTipText());
        assertNotNull(toggles.get(1).getToolTipText());
        assertNotNull(toggles.get(0).getAccessibleContext().getAccessibleDescription());
    }

    @Test
    void theLockControlIsReachableAndOperableFromTheKeyboard() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 9)),
                Collections.emptySet(), Collections.emptyList()));

        final JToggleButton toggle = lockToggles(panelFor(viewModel)).get(0);

        assertTrue(toggle.isFocusable(), "a control nobody can tab to is not a control");
        assertTrue(toggle.isEnabled());
        // doClick is what Space triggers on a focused button, so this is the keyboard path.
        SwingUtilities.invokeAndWait(toggle::doClick);
        assertTrue(viewModel.getState().getLockedEventIds().contains("museum"),
                "activating from the keyboard must reach the same toggleLock as a mouse click");
    }

    @Test
    void clickingTheLockTogglesTheExistingLockStateRatherThanASecondOne() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 9)),
                Collections.emptySet(), Collections.emptyList()));
        final DayPlanPanel panel = panelFor(viewModel);

        SwingUtilities.invokeAndWait(() -> lockToggles(panel).get(0).doClick());
        assertEquals(Collections.singleton("museum"), viewModel.getState().getLockedEventIds());

        SwingUtilities.invokeAndWait(() -> lockToggles(panel).get(0).doClick());
        assertTrue(viewModel.getState().getLockedEventIds().isEmpty(),
                "a second press unlocks; there is one lock system, not two");
    }

    @Test
    void generatedTravelRowsCarryNoLockControl() throws Exception {
        final ScheduledEvent travel = new ScheduledEvent("travel-1", null, LocalTime.of(10, 0),
                LocalTime.of(10, 30), EventType.TRAVEL, "Travel to market");
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Arrays.asList(event("museum", 9), travel),
                Collections.emptySet(), Collections.emptyList()));

        assertEquals(1, lockToggles(panelFor(viewModel)).size(),
                "the scheduler generates travel, so pinning it would mean nothing");
    }

    @Test
    void lockStateSurvivesGeneratingAndClearingAPreview() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 9)),
                new LinkedHashSet<>(Collections.singletonList("museum")),
                Collections.emptyList()));
        final DayPlanPanel panel = panelFor(viewModel);
        assertTrue(lockToggles(panel).get(0).isSelected());

        SwingUtilities.invokeAndWait(() -> {
            viewModel.setState(
                    viewModel.getState().loading("Working..."));
        });
        SwingUtilities.invokeAndWait(() -> {
            viewModel.setState(
                    viewModel.getState().clearedPreview("Cancelled"));
        });

        assertTrue(viewModel.getState().getLockedEventIds().contains("museum"));
        assertTrue(lockToggles(panel).get(0).isSelected(),
                "the pin must still read as pinned after a Preview comes and goes");
    }

    // --- 3. the Preview reads as a proposal, not a second itinerary --------------------

    private static DayPlanState previewState() {
        final List<PreviewRowView> rows = Arrays.asList(
                new PreviewRowView("museum", "Royal Ontario Museum",
                        PreviewRowView.Kind.ACTIVITY, LocalTime.of(11, 0), LocalTime.of(12, 0),
                        true, false, "you locked this time",
                        Collections.singletonList("you locked this time")),
                new PreviewRowView("travel-market", "Travel to St Lawrence Market",
                        PreviewRowView.Kind.TRAVEL, LocalTime.of(12, 0), LocalTime.of(12, 37),
                        false, false, "", Collections.emptyList()),
                new PreviewRowView("market", "St Lawrence Market",
                        PreviewRowView.Kind.ACTIVITY, LocalTime.of(12, 37), LocalTime.of(13, 37),
                        false, true, "a usual mealtime",
                        Arrays.asList("a usual mealtime", "less travel than before")));
        return new DayPlanState("trip-1", Arrays.asList(event("museum", 9), event("market", 14)),
                "Proposed schedule", false, Collections.emptyList(),
                AutoScheduleStatus.PREVIEW, rows,
                new PreviewMetricsView(0, 132, 270, 60, 2, 3, 200),
                Collections.singletonList("Travel times may include estimates."),
                "Arranged for less travel", true, false, "", "fingerprint",
                // The presenter carries the traveller's locks through unchanged, so a row
                // flagged locked is always in this set too.
                Collections.singleton("museum"));
    }

    /**
     * Every figure is on screen the moment the Preview opens.
     *
     * <p>They used to sit behind a disclosure, which meant the one thing that answers "is this
     * actually better?" was a click away and unlabelled arrows stood in for it.</p>
     */
    @Test
    void completeBeforeAndAfterFiguresAreVisibleWithoutOpeningAnything() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
        final DayPlanPanel panel = panelFor(viewModel);

        final String text = allText(panel);
        assertFalse(text.contains("0 → 132"),
                "the ambiguous arrow cards should be gone");
        assertTrue(text.contains("Before 0 min") && text.contains("Proposed 132 min"), text);
        assertTrue(text.contains("Before 270 min") && text.contains("Proposed 60 min"), text);
        assertTrue(text.contains("2 of 3"), text);
    }

    /**
     * A trade-off is stated plainly rather than left out of a positive-only summary. It is
     * part of the figures themselves now, so it cannot be missed by not opening a panel.
     */
    @Test
    void aWorseFigureIsReportedAsATradeOffRatherThanOmitted() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewState());

        final String text = allText(panelFor(viewModel));

        assertTrue(text.contains("132 min more"),
                "travel got worse in this fixture and the screen must say so: " + text);
    }

    /**
     * On a narrow window the two columns collapse into one, and the order matters: the
     * reasoning goes above the schedule. Below it, a full day of timeline stood between the
     * proposal and the only thing explaining it.
     */
    @Test
    void theReasoningSitsAboveTheTimelineOnANarrowWindow() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewWithImprovements(
                Collections.singletonList(new ImprovementView("\u23f3",
                        "63 min of waiting removed", "Less dead time"))));
        final DayPlanPanel panel = panelFor(viewModel);
        final javax.swing.JFrame host = new javax.swing.JFrame();
        SwingUtilities.invokeAndWait(() -> {
            host.setUndecorated(true);
            host.getContentPane().add(panel);
            host.setSize(DayPlanPanel.WIDE_LAYOUT_MINIMUM - 90, 900);
            host.addNotify();
            host.validate();
        });
        resizeTo(panel, DayPlanPanel.WIDE_LAYOUT_MINIMUM - 90);
        SwingUtilities.invokeAndWait(host::validate);

        final int[] positions = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            final ScheduleImprovementsPanel reasoning = stackIn(panel);
            assertNotNull(reasoning, "the Preview must show its reasoning: " + allText(panel));
            positions[0] = yWithin(panel, reasoning);
            final Container timeline = timelineIn(panel);
            assertNotNull(timeline, "the schedule is drawn into a timeline");
            positions[1] = yWithin(panel, timeline);
        });

        assertTrue(positions[0] < positions[1],
                "the reasoning belongs above the schedule when there is only one column "
                        + "(reasoning=" + positions[0] + ", timeline=" + positions[1] + ")");
        host.dispose();
    }

    /**
     * A proposal running late morning to mid afternoon should draw late morning to mid
     * afternoon, not nine to nine with the schedule adrift in the middle of it.
     */
    @Test
    void theTimelineIsFittedToTheProposalRatherThanToTheWholeDay() throws Exception {
        final DayPlanViewModel idle = new DayPlanViewModel(planWith(
                Arrays.asList(event("museum", 9)), Collections.emptySet(),
                Collections.emptyList()));
        final DayPlanPanel wholeDay = panelFor(idle);
        final DayPlanPanel proposal = panelFor(new DayPlanViewModel(previewState()));

        final int[] heights = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            heights[0] = timelineIn(wholeDay).getPreferredSize().height;
            heights[1] = timelineIn(proposal).getPreferredSize().height;
        });

        assertTrue(heights[1] < heights[0],
                "the previewed timeline should be shorter than a whole empty day (day="
                        + heights[0] + ", proposal=" + heights[1] + ")");
    }

    /** The timeline the panel builds its cards into, found by its accessible name. */
    private static Container timelineIn(Component root) {
        for (Component component : all(root)) {
            if ("Day schedule timeline".equals(component.getName())) {
                return (Container) component;
            }
        }
        return null;
    }

    private static boolean isTravelCard(Component card) {
        for (Component component : all(card)) {
            final String text = component instanceof JLabel ? ((JLabel) component).getText() : null;
            if (text != null && text.contains("Travel to")) {
                return true;
            }
        }
        return false;
    }

    /**
     * A travel block has no activity behind it, so the card takes its name from the notes —
     * and then printed the notes again as the subtitle, giving every journey its own name
     * twice over.
     */
    @Test
    void aTravelCardNamesItsJourneyOnce() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
        final DayPlanPanel panel = panelFor(viewModel);

        int mentions = 0;
        for (Component component : all(panel)) {
            final String text = component instanceof JLabel ? ((JLabel) component).getText() : null;
            if (text != null && text.contains("Travel to St Lawrence Market")) {
                mentions++;
            }
        }

        assertEquals(1, mentions, "the journey is named once on its card, not twice");
    }

    /**
     * A short journey is drawn at a readable minimum height and painted in front, and the
     * activity it reaches reserves that overrun as blank padding.
     *
     * <p>Both were previously impossible at once: the connector was sent to the back, so a
     * ten-minute walk was legible only in the sliver of gap it happened to have. Reserving
     * the overrun means neither has to lose — and the activity still begins at its true
     * time, only its contents start lower.</p>
     */
    @Test
    void aShortJourneyStaysReadableWithoutCoveringTheActivityItReaches() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
        final DayPlanPanel panel = panelFor(viewModel);

        final int[] depths = new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE};
        SwingUtilities.invokeAndWait(() -> {
            final Container timeline = timelineIn(panel);
            assertNotNull(timeline, "the schedule is drawn into a timeline");
            for (Component card : timeline.getComponents()) {
                // Swing paints the lowest z-order index last, so "in front" means smaller.
                final int depth = timeline.getComponentZOrder(card);
                if (isTravelCard(card)) {
                    depths[0] = Math.max(depths[0], depth);
                }
                else {
                    depths[1] = Math.min(depths[1], depth);
                }
            }
        });

        assertTrue(depths[0] != Integer.MIN_VALUE, "the fixture has a travel row");
        assertTrue(depths[1] != Integer.MAX_VALUE, "the fixture has activity rows");
        assertTrue(depths[0] < depths[1],
                "every travel connector paints in front of every activity (rearmost travel="
                        + depths[0] + ", frontmost activity=" + depths[1] + ")");
    }

    /** A connector is never drawn shorter than it can be read at. */
    @Test
    void aShortJourneyIsDrawnAtALeastItsMinimumReadableHeight() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
        final DayPlanPanel panel = panelFor(viewModel);
        // Without a peer nothing gets real bounds, and every card measures zero high.
        final javax.swing.JFrame host = new javax.swing.JFrame();
        SwingUtilities.invokeAndWait(() -> {
            host.setUndecorated(true);
            host.getContentPane().add(panel);
            host.setSize(DayPlanPanel.WIDE_LAYOUT_MINIMUM + 120, 900);
            host.addNotify();
            host.validate();
        });
        SwingUtilities.invokeAndWait(() -> { });

        final int[] shortest = new int[]{Integer.MAX_VALUE};
        SwingUtilities.invokeAndWait(() -> {
            final Container timeline = timelineIn(panel);
            // The timeline positions its own children, and a scroll viewport may not have
            // asked it to yet; laying it out explicitly is what gives the cards bounds.
            timeline.setSize(600, timeline.getPreferredSize().height);
            timeline.doLayout();
            for (Component card : timeline.getComponents()) {
                if (isTravelCard(card)) {
                    shortest[0] = Math.min(shortest[0], card.getHeight());
                }
            }
        });

        assertTrue(shortest[0] >= DayPlanPanel.MINIMUM_CONNECTOR_HEIGHT,
                "a journey drawn at " + shortest[0] + "px cannot be read");
        host.dispose();
    }
    /**
     * Which activities the schedule actually changed is the whole point of a Preview, so
     * the two markers had to survive the move onto the timeline. The card has no badge
     * slot, so "moved" leads the subtitle and locking keeps its own toggle.
     */

    @Test
    void movedAndLockedStaySignpostedOnTheTimelineCards() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
        final DayPlanPanel panel = panelFor(viewModel);

        final String text = allText(panel);

        assertTrue(text.contains("moved"), "the moved activity must say so: " + text);
        assertFalse(text.contains("[locked]"), "the bracketed form should be gone");
        assertFalse(text.contains("[moved]"), "the bracketed form should be gone");

        boolean lockedToggleShown = false;
        for (JToggleButton toggle : lockToggles(panel)) {
            final String name = toggle.getAccessibleContext().getAccessibleName();
            lockedToggleShown |= name != null && name.startsWith("Unlock ");
        }
        assertTrue(lockedToggleShown,
                "the locked activity keeps a toggle offering to unlock it");
    }

    @Test
    void warningsAppearInTheirOwnBandAndKeepTheirWording() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
        final DayPlanPanel panel = panelFor(viewModel);

        assertTrue(allText(panel).contains("Travel times may include estimates."));
        // The search-limit caveat is generated by the panel, not the state, so check it too.
        assertTrue(allText(panel).contains("best arrangement found within the search limit"),
                "a search that hit its limit must say so: " + allText(panel));
    }

    /**
     * There is no disclosure any more, and nothing is hidden by its absence.
     *
     * <p>It duplicated the figures above it, the tiles beside it and the reasons already
     * printed on the rows themselves, so a traveller had to open a panel to be told three
     * things they could already see. Per-activity reasons live on their own rows.</p>
     */
    @Test
    void thereIsNoDisclosureAndTheReasonsAreOnTheRowsThemselves() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
        final DayPlanPanel panel = panelFor(viewModel);

        final String text = allText(panel);
        assertNull(buttonNamed(panel, "Why these changes? ▸"),
                "the disclosure should be gone: " + text);
        assertFalse(text.contains("Why this schedule?") || text.contains("Why these changes?"),
                "and nothing should still offer to expand: " + text);
        assertTrue(text.contains("you locked this time"),
                "a pinned row still says why, on the row: " + text);
        assertTrue(text.contains("a usual mealtime"),
                "and so does a mealtime row: " + text);
    }

    @Test
    void conflictMessagesAreNeverHiddenBehindTheDisclosure() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                "trip-1", Collections.singletonList(event("museum", 9)),
                "Royal Ontario Museum is locked to a time you marked as unavailable.", true,
                Collections.emptyList(), AutoScheduleStatus.CONFLICT, Collections.emptyList(),
                null, Collections.emptyList(), "", true, true, "", "",
                Collections.emptySet()));

        final String text = allText(panelFor(viewModel));

        assertTrue(text.contains("locked to a time you marked as unavailable"),
                "a conflict is always visible: " + text);
    }

    // --- 4. actions stay stable and Apply is primary only when it can be used ----------

    @Test
    void applyAndCancelAppearOnlyWhileAProposalIsOnScreen() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 9)),
                Collections.emptySet(), Collections.emptyList()));
        final DayPlanPanel panel = panelFor(viewModel);

        assertFalse(buttonNamed(panel, "Apply").isVisible(), "nothing to apply while idle");
        assertFalse(buttonNamed(panel, "Cancel").isVisible());
        assertTrue(buttonNamed(panel, "Autoschedule").isVisible());

        SwingUtilities.invokeAndWait(() -> viewModel.setState(previewState()));

        assertTrue(buttonNamed(panel, "Apply").isVisible());
        assertTrue(buttonNamed(panel, "Apply").isEnabled());
        assertTrue(buttonNamed(panel, "Cancel").isVisible());
        assertFalse(buttonNamed(panel, "Autoschedule").isVisible(),
                "exactly one primary action is on screen at a time");
        assertNotNull(buttonNamed(panel, "Calendar View"), "Calendar stays available");
    }

    @Test
    void theOldMockupActionIsStillGone() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 9)),
                Collections.emptySet(), Collections.emptyList()));

        assertNull(buttonNamed(panelFor(viewModel), "Optimize Itinerary"));
    }

    // --- 5. long content must not widen the panel --------------------------------------

    @Test
    void aVeryLongWeatherLineIsTruncatedWithTheFullTextInATooltip() throws Exception {
        final String verbose = "Persistent heavy rain with gusting north-easterly winds and a real "
                + "risk of localised surface flooding across the downtown core this afternoon";
        final WeatherWarning warning = new WeatherWarning(new Location(43.65, -79.38, "Toronto"),
                LocalTime.of(13, 0), "Rain", WeatherSeverity.HIGH, verbose);
        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 13)),
                Collections.emptySet(), Collections.singletonList(warning)));

        final DayPlanPanel panel = panelFor(viewModel);
        JLabel weatherLine = null;
        for (Component component : all(panel)) {
            if (component instanceof JLabel
                    && ((JLabel) component).getText().startsWith("1:00 PM · Rain")) {
                weatherLine = (JLabel) component;
            }
        }

        assertNotNull(weatherLine, "the forecast line should render");
        assertTrue(weatherLine.getText().length() <= 64,
                "a long forecast must not be allowed to widen the panel: "
                        + weatherLine.getText());
        assertTrue(weatherLine.getText().endsWith("…"), "truncation should be visible");
        assertNotNull(weatherLine.getToolTipText(), "the full reading stays reachable");
        assertTrue(weatherLine.getToolTipText().contains("surface flooding"),
                "nothing is lost, only folded away");
    }

    // --- 5b. the improvements stack ----------------------------------------------------

    private static DayPlanState previewWithImprovements(List<ImprovementView> improvements) {
        final DayPlanState base = previewState();
        return new DayPlanState(base.getTripId(), base.getEvents(), base.getMessage(),
                base.isError(), base.getHourlyWeather(), base.getStatus(),
                base.getPreviewRows(), base.getMetrics(), base.getWarnings(),
                base.getObjectiveSummary(), base.isKeptCurrentOrder(),
                base.isSearchCompletedWithinLimit(), base.getTravelQualityNote(),
                base.getPreviewFingerprint(), base.getLockedEventIds(), improvements);
    }

    private static ScheduleImprovementsPanel stackIn(Component root) {
        for (Component component : all(root)) {
            if (component instanceof ScheduleImprovementsPanel) {
                return (ScheduleImprovementsPanel) component;
            }
        }
        return null;
    }

    @Test
    void provenImprovementsRenderAsStackedCardsWithAMarkerAndTheActivity() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(previewWithImprovements(Arrays.asList(
                new ImprovementView("\u23f3", "63 min of waiting removed", "Less dead time"),
                new ImprovementView("\u2600", "Moved into daylight", "High Park"))));
        final DayPlanPanel panel = panelFor(viewModel);

        final String text = allText(panel);
        assertTrue(text.contains("SCHEDULE IMPROVEMENTS"), text);
        assertTrue(text.contains("63 min of waiting removed"), text);
        assertTrue(text.contains("Moved into daylight"), text);
        assertTrue(text.contains("High Park"), "the card names its activity: " + text);
        // The glyph is present, so the categories survive being printed in grey.
        assertTrue(text.contains("\u23f3") && text.contains("\u2600"),
                "each card carries a marker that is not a colour");
        assertNotNull(stackIn(panel));
    }

    @Test
    void anHonestEmptyStateAppearsRatherThanAnInventedCard() throws Exception {
        final DayPlanViewModel viewModel = new DayPlanViewModel(
                previewWithImprovements(Collections.emptyList()));

        final String text = allText(panelFor(viewModel));

        assertTrue(text.contains(ScheduleImprovementsPanel.NOTHING_IMPROVED), text);
        assertFalse(text.contains("min of waiting removed"),
                "nothing improved, so nothing may be claimed");
    }

    /**
     * Resizes the panel and waits for the re-render the resize triggers.
     *
     * <p>Crossing the width threshold moves the improvements stack, and the panel does that
     * from a {@code componentResized} listener. AWT delivers that event through the event
     * queue, so it has not been handled when {@code setSize} returns — asserting straight
     * afterwards is a race that happens to pass on a fast machine and fails under a virtual
     * display. The second empty block drains the queue: the EDT runs events in order, so
     * once it runs, the resize has already been handled.</p>
     */
    private static void resizeTo(DayPlanPanel panel, int width) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.setSize(width, 700);
            panel.doLayout();
        });
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void theStackRendersInBothWideAndNarrowLayouts() throws Exception {
        final List<ImprovementView> improvements = Collections.singletonList(
                new ImprovementView("\u23f3", "63 min of waiting removed", "Less dead time"));
        final DayPlanViewModel viewModel =
                new DayPlanViewModel(previewWithImprovements(improvements));
        final DayPlanPanel panel = panelFor(viewModel);

        resizeTo(panel, DayPlanPanel.WIDE_LAYOUT_MINIMUM + 120);
        assertNotNull(stackIn(panel), "wide: the stack sits beside the schedule");
        assertTrue(allText(panel).contains("63 min of waiting removed"));

        resizeTo(panel, DayPlanPanel.WIDE_LAYOUT_MINIMUM - 200);
        assertNotNull(stackIn(panel), "narrow: the same stack moves below the schedule");
        assertTrue(allText(panel).contains("63 min of waiting removed"),
                "the cards must survive the narrow layout, not disappear with the sidebar");
    }

    @Test
    void theStackIsReusableWithoutADayPlanAroundIt() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "building components needs a display");
        final ScheduleImprovementsPanel[] standalone = new ScheduleImprovementsPanel[1];

        SwingUtilities.invokeAndWait(() -> {
            standalone[0] = new ScheduleImprovementsPanel(
                    Collections.singletonList(new ImprovementView("\u2600",
                            "Moved into daylight", "High Park")));
        });

        // Nothing about a Day Plan was needed to build it, which is what lets it move to a
        // calendar view later.
        assertTrue(allText(standalone[0]).contains("Moved into daylight"));
        assertEquals(ScheduleImprovementsPanel.PREFERRED_WIDTH,
                standalone[0].getPreferredSize().width,
                "a fixed width is what makes it droppable into a sidebar");
    }

    // --- 6. nothing is leaked ----------------------------------------------------------

    @Test
    void buildingThePanelOpensNoWindow() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "window counting needs a display");
        final int before = java.awt.Window.getWindows().length;

        final DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 9)),
                Collections.emptySet(), Collections.emptyList()));
        panelFor(viewModel);

        assertEquals(before, java.awt.Window.getWindows().length,
                "a panel is not a window; the Day Plan must not open one to render");
    }

    /**
     * Explanations belong with the proposal, not after the whole timeline.
     *
     * <p>A five-activity day becomes nine rows once travel is interleaved. With the "Why
     * this schedule?" control rendered after those rows, the one thing that justifies the
     * schedule was the one thing a user had to scroll the entire day to reach — which is
     * exactly what was reported. On a wide window it now sits in a column beside the
     * schedule, so this pins it by horizontal position: right of the timeline, not under it.
     */
    @Test
    void theExplanationSitsBesideTheTimelineRatherThanBelowIt() throws Exception {
        final List<ImprovementView> improvements = Collections.singletonList(
                new ImprovementView("\u23f3", "63 min of waiting removed", "Less dead time"));
        final DayPlanViewModel viewModel =
                new DayPlanViewModel(previewWithImprovements(improvements));
        final DayPlanPanel panel = panelFor(viewModel);
        // A component with no peer never gets real bounds, so host it in a frame that is
        // never shown; addNotify + validate is what gives every child a position.
        final javax.swing.JFrame host = new javax.swing.JFrame();
        SwingUtilities.invokeAndWait(() -> {
            host.setUndecorated(true);
            host.getContentPane().add(panel);
            host.setSize(DayPlanPanel.WIDE_LAYOUT_MINIMUM + 120, 900);
            host.addNotify();
            host.validate();
        });
        resizeTo(panel, DayPlanPanel.WIDE_LAYOUT_MINIMUM + 120);
        SwingUtilities.invokeAndWait(host::validate);

        // Rendering rebuilds the schedule on the event thread, so a tree read from the test
        // thread can land between "clear" and "refill" and see a panel that never existed.
        final int[] positions = new int[3];
        SwingUtilities.invokeAndWait(() -> {
            final ScheduleImprovementsPanel reasoning = stackIn(panel);
            assertNotNull(reasoning, "the Preview must show its reasoning");
            positions[0] = xWithin(panel, reasoning);
            positions[1] = -1;
            for (Component component : all(panel)) {
                final String text = component instanceof JLabel
                        ? ((JLabel) component).getText() : null;
                if (text != null && text.contains("Travel to")) {
                    positions[1] = Math.max(positions[1], xWithin(panel, component));
                    positions[2]++;
                }
            }
        });

        assertTrue(positions[1] > 0, "the proposal should contain travel rows");
        assertTrue(positions[0] > positions[1],
                "the reasoning sits in the column beside the schedule, not below it (reasoning x="
                        + positions[0] + ", schedule x=" + positions[1] + ")");
        assertEquals(1, positions[2],
                "the schedule is drawn once; a second copy below it is the duplicate that "
                        + "the timeline takeover removed");
        host.dispose();
    }

    /** X position of a component relative to the panel, summing container offsets. */
    private static int xWithin(Component root, Component target) {
        int x = 0;
        Component cursor = target;
        while (cursor != null && cursor != root) {
            x += cursor.getX();
            cursor = cursor.getParent();
        }
        return x;
    }

    /** Y position of a component relative to the panel, summing container offsets. */
    private static int yWithin(Component root, Component target) {
        int y = 0;
        Component cursor = target;
        while (cursor != null && cursor != root) {
            y += cursor.getY();
            cursor = cursor.getParent();
        }
        return y;
    }
}
