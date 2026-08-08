package trippy.adapters.views;

import trippy.adapters.controllers.TripOptionsController;
import trippy.adapters.viewmodels.TripOptionsState;
import trippy.adapters.viewmodels.TripOptionsViewModel;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Focused modal editor for the active Day Plan's date and time boundaries. */
public final class TripOptionsDialog {
    private final java.awt.Component owner;
    private final TripOptionsViewModel viewModel;
    private final TripOptionsController controller;
    private final DateSelectionButton date;
    private final TimeSelectorPanel start;
    private final TimeSelectorPanel end;
    private final JLabel feedback = new JLabel();
    private JDialog dialog;

    public TripOptionsDialog(java.awt.Component owner,
                             TripOptionsViewModel viewModel,
                             TripOptionsController controller) {
        if (viewModel == null || controller == null) {
            throw new IllegalArgumentException("Trip Options dialog dependencies are required");
        }
        this.owner = owner;
        this.viewModel = viewModel;
        this.controller = controller;
        TripOptionsState state = viewModel.getState();
        date = new DateSelectionButton(state.getDate());
        start = new TimeSelectorPanel(state.getStartTime());
        end = new TimeSelectorPanel(state.getEndTime());
    }

    public void showDialog() {
        TripOptionsState state = viewModel.getState();
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        addField(fields, 0, "Destination", boldLabel(state.getDestination()));
        addField(fields, 1, "Trip date", date);
        addField(fields, 2, "Day starts", start);
        addField(fields, 3, "Day ends", end);
        feedback.setFont(SwingTheme.SMALL);
        GridBagConstraints feedbackConstraints = constraints(1, 4);
        feedbackConstraints.fill = GridBagConstraints.HORIZONTAL;
        fields.add(feedback, feedbackConstraints);

        JButton cancel = SwingTheme.secondaryButton("Cancel");
        JButton save = SwingTheme.primaryButton("Save Options");
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        actions.add(cancel);
        actions.add(save);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(fields, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        dialog = new JDialog(SwingUtilities.getWindowAncestor(owner),
                "Day Plan Options", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(content);
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            controller.execute(date.getDate(), start.getTime(), end.getTime());
            TripOptionsState updated = viewModel.getState();
            renderFeedback(updated);
            if (!updated.isError()) dialog.dispose();
        });
        dialog.getRootPane().setDefaultButton(save);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private void renderFeedback(TripOptionsState state) {
        feedback.setText(state.getMessage());
        feedback.setForeground(state.isError() ? SwingTheme.ERROR : SwingTheme.SUCCESS);
    }

    private static JLabel boldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SwingTheme.BODY.deriveFont(java.awt.Font.BOLD));
        label.setForeground(SwingTheme.NAVY);
        return label;
    }

    private static void addField(JPanel panel, int row, String name, java.awt.Component value) {
        JLabel label = new JLabel(name);
        label.setFont(SwingTheme.BODY);
        label.setForeground(SwingTheme.NAVY);
        panel.add(label, constraints(0, row));
        GridBagConstraints valueConstraints = constraints(1, row);
        valueConstraints.weightx = 1;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(value, valueConstraints);
    }

    private static GridBagConstraints constraints(int column, int row) {
        GridBagConstraints result = new GridBagConstraints();
        result.gridx = column;
        result.gridy = row;
        result.anchor = GridBagConstraints.WEST;
        result.insets = new Insets(6, 6, 6, 6);
        return result;
    }
}
