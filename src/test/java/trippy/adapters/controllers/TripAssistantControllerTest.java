package trippy.adapters.controllers;

import trippy.adapters.presenters.TripAssistantPresenter;
import trippy.adapters.viewmodels.TripAssistantState;
import trippy.adapters.viewmodels.TripAssistantViewModel;
import trippy.application.tripassistant.TripAssistantInputData;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TripAssistantControllerTest {

    @Test
    void dispatchesAssistantWorkAwayFromTheEventThread() throws Exception {
        TripAssistantViewModel viewModel = new TripAssistantViewModel(
                new TripAssistantState(Collections.emptyList(), false, ""));
        TripAssistantPresenter presenter = new TripAssistantPresenter(viewModel);
        AtomicBoolean useCaseRanOnEdt = new AtomicBoolean(true);
        AtomicReference<TripAssistantInputData> received = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        TripAssistantController controller = new TripAssistantController(input -> {
            useCaseRanOnEdt.set(SwingUtilities.isEventDispatchThread());
            received.set(input);
            finished.countDown();
        }, () -> "trip-7", presenter, viewModel, new SwingTaskRunner());

        SwingUtilities.invokeAndWait(() -> controller.execute("What fits this afternoon?"));

        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertFalse(useCaseRanOnEdt.get());
        assertEquals("trip-7", received.get().getTripId());
        assertEquals("What fits this afternoon?", received.get().getQuestion());
        assertTrue(viewModel.getState().isLoading());
        assertEquals(1, viewModel.getState().getMessages().size());
    }
}
