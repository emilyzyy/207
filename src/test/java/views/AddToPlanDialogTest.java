package views;

import use_case.usecases.AvailableTimeSlotFinder;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.valueobjects.EventType;
import interface_adapter.mock.MockPlacesService;
import java.awt.GraphicsEnvironment;
import java.time.LocalTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

final class AddToPlanDialogTest {
    @Test
    void constructsAfterTripBoundsAndShowsTheExistingDayPlan() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Activity existing = new MockPlacesService().findAll().get(0);
        Activity proposed = new MockPlacesService().findAll().get(1);
        ScheduledEvent planned = new ScheduledEvent(
                "planned", existing, LocalTime.of(10, 0), LocalTime.of(11, 0),
                EventType.ACTIVITY, "Already planned");
        AvailableTimeSlotFinder.Slot slot = new AvailableTimeSlotFinder().find(
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                Collections.singletonList(planned));
        assertNotNull(slot);
        AtomicReference<AddToPlanDialog> built = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> built.set(new AddToPlanDialog(
                new JPanel(), proposed, Collections.singletonList(planned),
                LocalTime.of(9, 0), LocalTime.of(18, 0), null, slot)));

        assertEquals(1, built.get().displayedPlannedActivityCount());
        SwingUtilities.invokeAndWait(() -> built.get().dispose());
    }
}
