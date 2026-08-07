package closeai.adapters.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import closeai.adapters.controllers.AutoScheduleController;
import closeai.adapters.controllers.TaskRunner;
import closeai.adapters.viewmodels.AutoScheduleStatus;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.PreviewMetricsView;
import closeai.adapters.viewmodels.PreviewRowView;
import closeai.application.autoschedule.AutoScheduleApplyInputData;
import closeai.application.autoschedule.AutoScheduleInputBoundary;
import closeai.application.autoschedule.AutoScheduleInputData;
import closeai.application.autoschedule.WeatherOption;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.WeatherSeverity;
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
        SwingUtilities.invokeAndWait(() -> built[0] = new DayPlanPanel(viewModel,
                new AutoScheduleController(new RecordingUseCase(), viewModel,
                        TaskRunner.immediate())));
        return built[0];
    }

    // --- component search helpers -----------------------------------------------------

    private static List<Component> all(Component root) {
        List<Component> found = new ArrayList<>();
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
        List<JToggleButton> toggles = new ArrayList<>();
        for (Component component : all(root)) {
            if (component instanceof JToggleButton) {
                String name = component.getAccessibleContext().getAccessibleName();
                if (name != null && (name.startsWith("Lock ") || name.startsWith("Unlock "))) {
                    toggles.add((JToggleButton) component);
                }
            }
        }
        return toggles;
    }

    private static String allText(Component root) {
        StringBuilder text = new StringBuilder();
        for (Component component : all(root)) {
            if (component instanceof JLabel) {
                text.append(((JLabel) component).getText()).append(' ');
            } else if (component instanceof AbstractButton) {
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
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Arrays.asList(event("museum", 9), event("market", 15)),
                Collections.emptySet(), Collections.emptyList()));

        String text = allText(panelFor(viewModel));

        assertTrue(text.contains("9:00 AM – 10:00 AM"), "morning row: " + text);
        assertTrue(text.contains("3:00 PM – 4:00 PM"), "afternoon row: " + text);
        assertFalse(text.contains("15:00"), "no 24-hour time should survive: " + text);
    }

    @Test
    void weatherTimestampsAlsoUseATwelveHourClock() throws Exception {
        WeatherWarning noon = new WeatherWarning(new Location(43.65, -79.38, "Toronto"),
                LocalTime.of(13, 0), "Rain", WeatherSeverity.MEDIUM, "65% precipitation");
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 13)),
                Collections.emptySet(), Collections.singletonList(noon)));

        String text = allText(panelFor(viewModel));

        assertTrue(text.contains("1:00 PM · Rain"), "weather line: " + text);
    }

    // --- 2. the lock control is visible, distinguishable and reachable ----------------

    @Test
    void everyActivityCarriesALockToggleShowingItsState() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Arrays.asList(event("museum", 9), event("market", 15)),
                new LinkedHashSet<>(Collections.singletonList("museum")),
                Collections.emptyList()));

        List<JToggleButton> toggles = lockToggles(panelFor(viewModel));

        assertEquals(2, toggles.size(), "one lock control per activity");
        JToggleButton locked = toggles.get(0);
        JToggleButton unlocked = toggles.get(1);

        assertTrue(locked.isSelected(), "the pinned activity's control reads as locked");
        assertFalse(unlocked.isSelected());
        // The two states are told apart by the icon that is drawn, not only by colour.
        assertNotNull(locked.getIcon());
        assertNotNull(unlocked.getIcon());
    }

    @Test
    void theLockControlNamesTheActivityAndWhatPressingItWillDo() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Arrays.asList(event("museum", 9), event("market", 15)),
                new LinkedHashSet<>(Collections.singletonList("museum")),
                Collections.emptyList()));

        List<JToggleButton> toggles = lockToggles(panelFor(viewModel));

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
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 9)),
                Collections.emptySet(), Collections.emptyList()));

        JToggleButton toggle = lockToggles(panelFor(viewModel)).get(0);

        assertTrue(toggle.isFocusable(), "a control nobody can tab to is not a control");
        assertTrue(toggle.isEnabled());
        // doClick is what Space triggers on a focused button, so this is the keyboard path.
        SwingUtilities.invokeAndWait(toggle::doClick);
        assertTrue(viewModel.getState().getLockedEventIds().contains("museum"),
                "activating from the keyboard must reach the same toggleLock as a mouse click");
    }

    @Test
    void clickingTheLockTogglesTheExistingLockStateRatherThanASecondOne() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 9)),
                Collections.emptySet(), Collections.emptyList()));
        DayPlanPanel panel = panelFor(viewModel);

        SwingUtilities.invokeAndWait(() -> lockToggles(panel).get(0).doClick());
        assertEquals(Collections.singleton("museum"), viewModel.getState().getLockedEventIds());

        SwingUtilities.invokeAndWait(() -> lockToggles(panel).get(0).doClick());
        assertTrue(viewModel.getState().getLockedEventIds().isEmpty(),
                "a second press unlocks; there is one lock system, not two");
    }

    @Test
    void generatedTravelRowsCarryNoLockControl() throws Exception {
        ScheduledEvent travel = new ScheduledEvent("travel-1", null, LocalTime.of(10, 0),
                LocalTime.of(10, 30), EventType.TRAVEL, "Travel to market");
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Arrays.asList(event("museum", 9), travel),
                Collections.emptySet(), Collections.emptyList()));

        assertEquals(1, lockToggles(panelFor(viewModel)).size(),
                "the scheduler generates travel, so pinning it would mean nothing");
    }

    @Test
    void lockStateSurvivesGeneratingAndClearingAPreview() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 9)),
                new LinkedHashSet<>(Collections.singletonList("museum")),
                Collections.emptyList()));
        DayPlanPanel panel = panelFor(viewModel);
        assertTrue(lockToggles(panel).get(0).isSelected());

        SwingUtilities.invokeAndWait(() -> viewModel.setState(
                viewModel.getState().loading("Working...")));
        SwingUtilities.invokeAndWait(() -> viewModel.setState(
                viewModel.getState().clearedPreview("Cancelled")));

        assertTrue(viewModel.getState().getLockedEventIds().contains("museum"));
        assertTrue(lockToggles(panel).get(0).isSelected(),
                "the pin must still read as pinned after a Preview comes and goes");
    }

    // --- 3. the Preview reads as a proposal, not a second itinerary --------------------

    private static DayPlanState previewState() {
        List<PreviewRowView> rows = Arrays.asList(
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
        return new DayPlanState("trip-1", Collections.singletonList(event("museum", 9)),
                "Proposed schedule", false, Collections.emptyList(),
                AutoScheduleStatus.PREVIEW, rows,
                new PreviewMetricsView(0, 132, 270, 60, 2, 3, 200),
                Collections.singletonList("Travel times may include estimates."),
                "Arranged for less travel", true, false, "", "fingerprint",
                Collections.emptySet());
    }

    @Test
    void previewMetricsAreShownAsBeforeAndAfterFigures() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(previewState());

        String text = allText(panelFor(viewModel));

        assertTrue(text.contains("0 → 132"), "travel before and after: " + text);
        assertTrue(text.contains("270 → 60"), "waiting before and after: " + text);
        assertTrue(text.contains("2 of 3"), "activities moved: " + text);
        assertTrue(text.contains("travel, minutes") && text.contains("waiting, minutes"),
                "each figure says what it counts");
    }

    @Test
    void movedAndLockedAreBadgesRatherThanBracketedText() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(previewState());

        String text = allText(panelFor(viewModel));

        assertTrue(text.contains("Locked"), text);
        assertTrue(text.contains("Moved"), text);
        assertFalse(text.contains("[locked]"), "the bracketed form should be gone");
        assertFalse(text.contains("[moved]"), "the bracketed form should be gone");
    }

    @Test
    void warningsAppearInTheirOwnBandAndKeepTheirWording() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
        DayPlanPanel panel = panelFor(viewModel);

        assertTrue(allText(panel).contains("Travel times may include estimates."));
        // The search-limit caveat is generated by the panel, not the state, so check it too.
        assertTrue(allText(panel).contains("best arrangement found within the search limit"),
                "a search that hit its limit must say so: " + allText(panel));
    }

    @Test
    void reasoningIsCollapsedBehindADisclosureAndExpandsWithEveryReason() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(previewState());
        DayPlanPanel panel = panelFor(viewModel);

        AbstractButton disclosure = buttonNamed(panel, "▸ Why this schedule?");
        assertNotNull(disclosure, "there should be a collapsed disclosure: " + allText(panel));
        assertFalse(allText(panel).contains("less travel than before"),
                "reasons start hidden");

        SwingUtilities.invokeAndWait(disclosure::doClick);

        String expanded = allText(panel);
        assertTrue(expanded.contains("less travel than before"), expanded);
        assertTrue(expanded.contains("a usual mealtime"), expanded);
        assertTrue(expanded.contains("Times you pinned") || expanded.contains("Mealtimes"),
                "reasons are grouped under headings: " + expanded);
    }

    @Test
    void conflictMessagesAreNeverHiddenBehindTheDisclosure() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                "trip-1", Collections.singletonList(event("museum", 9)),
                "Royal Ontario Museum is locked to a time you marked as unavailable.", true,
                Collections.emptyList(), AutoScheduleStatus.CONFLICT, Collections.emptyList(),
                null, Collections.emptyList(), "", true, true, "", "",
                Collections.emptySet()));

        String text = allText(panelFor(viewModel));

        assertTrue(text.contains("locked to a time you marked as unavailable"),
                "a conflict is always visible: " + text);
    }

    // --- 4. actions stay stable and Apply is primary only when it can be used ----------

    @Test
    void applyAndCancelAppearOnlyWhileAProposalIsOnScreen() throws Exception {
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 9)),
                Collections.emptySet(), Collections.emptyList()));
        DayPlanPanel panel = panelFor(viewModel);

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
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 9)),
                Collections.emptySet(), Collections.emptyList()));

        assertNull(buttonNamed(panelFor(viewModel), "Optimize Itinerary"));
    }

    // --- 5. long content must not widen the panel --------------------------------------

    @Test
    void aVeryLongWeatherLineIsTruncatedWithTheFullTextInATooltip() throws Exception {
        String verbose = "Persistent heavy rain with gusting north-easterly winds and a real "
                + "risk of localised surface flooding across the downtown core this afternoon";
        WeatherWarning warning = new WeatherWarning(new Location(43.65, -79.38, "Toronto"),
                LocalTime.of(13, 0), "Rain", WeatherSeverity.HIGH, verbose);
        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 13)),
                Collections.emptySet(), Collections.singletonList(warning)));

        DayPlanPanel panel = panelFor(viewModel);
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

    // --- 6. nothing is leaked ----------------------------------------------------------

    @Test
    void buildingThePanelOpensNoWindow() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "window counting needs a display");
        int before = java.awt.Window.getWindows().length;

        DayPlanViewModel viewModel = new DayPlanViewModel(planWith(
                Collections.singletonList(event("museum", 9)),
                Collections.emptySet(), Collections.emptyList()));
        panelFor(viewModel);

        assertEquals(before, java.awt.Window.getWindows().length,
                "a panel is not a window; the Day Plan must not open one to render");
    }
}
