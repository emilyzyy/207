package closeai.application.usecases;

public final class ShareTripUseCase {
    private final GetTripSummaryUseCase summaries;
    public ShareTripUseCase(GetTripSummaryUseCase summaries) { this.summaries = summaries; }
    public String execute(String tripId) { return summaries.execute(tripId); }
}
