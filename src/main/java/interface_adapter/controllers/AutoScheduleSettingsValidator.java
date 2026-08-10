package interface_adapter.controllers;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import interface_adapter.viewmodels.TimeDisplay;

/**
 * Checks the settings dialog before anything is submitted.
 *
 * <p>The use case validates all of this again, and it has to: it is the layer that
 * guarantees correctness. This exists so the traveller finds out about a typo while the
 * dialog is still open, rather than after a wait and a failure message. Keeping it in a
 * plain class rather than inside the Swing dialog is what makes it testable.</p>
 */
public final class AutoScheduleSettingsValidator {

    /** Prefix for the numbered unavailable-period messages. */
    private static final String PERIOD = "Unavailable period ";

    /**
      * @param settings the s et ti ng s value
     * @param tripEnd   the trip's own closing hour, or null when unknown
     * @param tripStart the trip's own opening hour, or null when unknown
     * @return the problems found, in the order they should be shown; empty when usable
     */
    public List<String> validate(AutoScheduleSettings settings, LocalTime tripStart,
                                 LocalTime tripEnd) {
        final List<String> problems = new ArrayList<>();
        if (settings == null) {
            problems.add("Choose the hours you are available.");
            return problems;
        }

        final LocalTime start = settings.getAvailableStart();
        final LocalTime end = settings.getAvailableEnd();
        if (start == null || end == null) {
            problems.add("Choose both an available-from and an available-until time.");
            return problems;
        }
        if (!end.isAfter(start)) {
            problems.add("Available until must be later than available from.");
            return problems;
        }
        if (tripStart != null && tripEnd != null
                && (start.isBefore(tripStart) || end.isAfter(tripEnd))) {
            problems.add("Available hours must be within the trip's hours ("
                    + TimeDisplay.format(tripStart) + " to " + TimeDisplay.format(tripEnd)
                    + "). To extend your day, edit the trip settings.");
        }

        final List<AutoScheduleSettings.Window> windows = settings.getUnavailableWindows();
        for (int i = 0; i < windows.size(); i++) {
            final AutoScheduleSettings.Window window = windows.get(i);
            if (window.getStart() == null || window.getEnd() == null) {
                problems.add(PERIOD + (i + 1) + " needs a start and an end time.");
                continue;
            }
            if (!window.getEnd().isAfter(window.getStart())) {
                problems.add(PERIOD + (i + 1)
                        + " must end after it starts.");
                continue;
            }
            if (window.getStart().isBefore(start) || window.getEnd().isAfter(end)) {
                problems.add(PERIOD + (i + 1)
                        + " falls outside the hours you are available.");
            }
            for (int j = i + 1; j < windows.size(); j++) {
                final AutoScheduleSettings.Window other = windows.get(j);
                if (other.getStart() == null || other.getEnd() == null) {
                    continue;
                }
                final boolean overlaps = window.getStart().isBefore(other.getEnd())
                        && other.getStart().isBefore(window.getEnd());
                if (overlaps) {
                    problems.add("Unavailable periods " + (i + 1) + " and " + (j + 1)
                            + " overlap each other.");
                }
            }
        }
        return problems;
    }
}
