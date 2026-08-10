package interface_adapter.viewmodels;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable state for the share preview, day-plan images, and clipboard action. */
public final class ShareState {
    private final String shareText;
    private final String message;
    private final boolean error;
    private final List<BufferedImage> dayImages;

    public ShareState(String shareText, String message, boolean error) {
        this(shareText, message, error, Collections.<BufferedImage>emptyList());
    }

    public ShareState(String shareText, String message, boolean error,
                      List<BufferedImage> dayImages) {
        this.shareText = shareText == null ? "" : shareText;
        this.message = message == null ? "" : message;
        this.error = error;
        if (dayImages == null || dayImages.isEmpty()) {
            this.dayImages = Collections.emptyList();
        } else {
            this.dayImages = Collections.unmodifiableList(
                    new ArrayList<BufferedImage>(dayImages));
        }
    }

    public String getShareText() {
        return shareText;
    }

    public String getMessage() {
        return message;
    }

    public boolean isError() {
        return error;
    }

    /** One PNG card per trip day (empty when share failed or no images were produced). */
    public List<BufferedImage> getDayImages() {
        return dayImages;
    }

    public boolean canCopy() {
        return !shareText.trim().isEmpty() && !error;
    }

    public boolean canSaveImages() {
        return !error && !dayImages.isEmpty();
    }
}
