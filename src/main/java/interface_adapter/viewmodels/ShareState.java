package interface_adapter.viewmodels;

/** Immutable state for the share preview and clipboard action. */
public final class ShareState {
    private final String shareText;
    private final String message;
    private final boolean error;

    public ShareState(String shareText, String message, boolean error) {
        this.shareText = shareText == null ? "" : shareText;
        this.message = message == null ? "" : message;
        this.error = error;
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

    public boolean canCopy() {
        return !shareText.trim().isEmpty() && !error;
    }
}
