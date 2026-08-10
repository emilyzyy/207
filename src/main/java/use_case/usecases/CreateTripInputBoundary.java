package use_case.usecases;

/** Input boundary for creating and storing a new trip. */
public interface CreateTripInputBoundary {
    /**
     * Creates a new trip from the given input.
     *
     * @param inputData the validated trip details
     */
    void execute(CreateTripInputData inputData);
}
