package use_case.usecases;

/** Application boundary for profile load / update / sign-out. */
public interface ManageProfileInputBoundary {
    void execute(ManageProfileInputData inputData);
}
