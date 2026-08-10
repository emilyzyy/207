package use_case.usecases;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

/** Output boundary that lets an adapter present share results without framework coupling. */
public interface ShareTripOutputBoundary {
    void presentSuccess(String shareText);

    /**
     * Presents share text plus one day-plan image per trip day.
     * Default falls back to text-only success for existing adapters/tests.
     */
    default void presentSuccess(String shareText, List<BufferedImage> dayImages) {
        presentSuccess(shareText);
    }

    void presentFailure(String errorMessage);
}
