package closeai.application.autoschedule.testdoubles;

import closeai.application.autoschedule.AutoScheduleAppliedOutputData;
import closeai.application.autoschedule.AutoScheduleConflictOutputData;
import closeai.application.autoschedule.AutoScheduleOutputBoundary;
import closeai.application.autoschedule.AutoSchedulePreviewOutputData;

/** Captures whatever the use case presented, so tests can assert on it directly. */
public final class RecordingPresenter implements AutoScheduleOutputBoundary {

    private AutoSchedulePreviewOutputData preview;
    private AutoScheduleAppliedOutputData applied;
    private AutoScheduleConflictOutputData conflict;
    private String failure;

    @Override
    public void presentPreview(AutoSchedulePreviewOutputData outputData) {
        this.preview = outputData;
    }

    @Override
    public void presentApplied(AutoScheduleAppliedOutputData outputData) {
        this.applied = outputData;
    }

    @Override
    public void presentConflict(AutoScheduleConflictOutputData outputData) {
        this.conflict = outputData;
    }

    @Override
    public void presentFailure(String message) {
        this.failure = message;
    }

    public AutoSchedulePreviewOutputData getPreview() {
        return preview;
    }

    public AutoScheduleAppliedOutputData getApplied() {
        return applied;
    }

    public AutoScheduleConflictOutputData getConflict() {
        return conflict;
    }

    public String getFailure() {
        return failure;
    }
}
