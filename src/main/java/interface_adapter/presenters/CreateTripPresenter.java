package interface_adapter.presenters;

import java.util.function.Consumer;

import entity.entities.Trip;
import use_case.usecases.CreateTripOutputBoundary;
import use_case.usecases.CreateTripOutputData;

/** Presents new-trip creation through pluggable UI callbacks. */
public final class CreateTripPresenter implements CreateTripOutputBoundary {
    private Consumer<Trip> onCreated;
    private Consumer<String> onError;

    /**
     * Sets the callback invoked after a trip is created.
     *
     * @param onCreated receives the created trip
     */
    public void setOnCreated(Consumer<Trip> onCreated) {
        this.onCreated = onCreated;
    }

    /**
     * Sets the callback invoked when creation fails.
     *
     * @param onError receives the failure message
     */
    public void setOnError(Consumer<String> onError) {
        this.onError = onError;
    }

    @Override
    public void presentSuccess(CreateTripOutputData outputData) {
        if (onCreated != null) {
            onCreated.accept(outputData.getTrip());
        }
    }

    @Override
    public void presentFailure(String errorMessage) {
        if (onError != null) {
            onError.accept(errorMessage);
        }
    }
}
