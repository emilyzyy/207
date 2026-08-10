package use_case.usecases;

public final class ShareTripUseCase implements ShareTripInputBoundary {
    private final GetTripSummaryUseCase summaries;

    public ShareTripUseCase(GetTripSummaryUseCase summaries) {
        if (summaries == null) {
            throw new IllegalArgumentException("Trip summaries are required");
        }
        this.summaries = summaries;
    }

    @Override
    public String execute(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            throw new IllegalArgumentException("Create a trip before sharing");
        }
        return summaries.execute(tripId.trim());
    }
}
