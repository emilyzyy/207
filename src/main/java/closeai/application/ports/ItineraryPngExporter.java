package closeai.application.ports;

import closeai.application.usecases.ShareCardModel;

/**
 * Renders an itinerary share card as PNG bytes.
 * Implemented outside the application layer so AWT/ImageIO stay in infrastructure (DIP).
 */
public interface ItineraryPngExporter {
    byte[] export(ShareCardModel card);
}
