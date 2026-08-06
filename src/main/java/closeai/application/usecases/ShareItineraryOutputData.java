package closeai.application.usecases;

/** PNG bytes and suggested filename for a successful share export. */
public final class ShareItineraryOutputData {
    private final byte[] pngBytes;
    private final String suggestedFileName;

    public ShareItineraryOutputData(byte[] pngBytes, String suggestedFileName) {
        if (pngBytes == null || pngBytes.length == 0) {
            throw new IllegalArgumentException("PNG bytes are required");
        }
        if (suggestedFileName == null || suggestedFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Suggested file name is required");
        }
        this.pngBytes = pngBytes;
        this.suggestedFileName = suggestedFileName.trim();
    }

    public byte[] getPngBytes() {
        return pngBytes;
    }

    public String getSuggestedFileName() {
        return suggestedFileName;
    }
}
