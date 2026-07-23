package closeai;

import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.views.CloseAIFrame;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.time.LocalTime;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

final class SwingApplicationIntegrationTest {

    @Test
    void builderWiresOptimizerAndCalendarToTheSharedDayPlanState() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        System.setProperty("closeai.weather.mode", "mock");

        SwingUtilities.invokeAndWait(() -> {
            CloseAIFrame frame = new AppBuilder().buildSwingApplication();
            DayPlanViewModel sharedState = frame.getCalendarDialog().getViewModel();
            assertSame(sharedState, frame.getDayPlanPanel().getViewModel());
            assertEquals(3, sharedState.getState().getEvents().size());
            assertEquals(
                    LocalTime.of(10, 0),
                    sharedState.getState().getEvents().get(0).getStartTime());

            AbstractButton optimize =
                    findButton(frame, "Optimize Current Itinerary");
            assertNotNull(optimize);
            optimize.doClick();

            assertEquals(3, sharedState.getState().getEvents().size());
            assertEquals(
                    LocalTime.of(9, 0),
                    sharedState.getState().getEvents().get(0).getStartTime());
            assertEquals(
                    "Current itinerary compacted successfully",
                    sharedState.getState().getMessage());
            frame.dispose();
        });
    }

    private AbstractButton findButton(Component component, String text) {
        if (component instanceof AbstractButton
                && text.equals(((AbstractButton) component).getText())) {
            return (AbstractButton) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                AbstractButton found = findButton(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
