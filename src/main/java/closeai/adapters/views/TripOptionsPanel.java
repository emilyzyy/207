package closeai.adapters.views;

import closeai.adapters.controllers.TripSetupController;
import closeai.adapters.viewmodels.TripOptionsState;
import closeai.adapters.viewmodels.TripOptionsViewModel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Editable Create Trip and Trip Options view. */
public final class TripOptionsPanel extends JPanel {
    private final TripOptionsViewModel viewModel;
    private final TripSetupController controller;
    private final JTextField destination = new JTextField();
    private final JTextField date = new JTextField();
    private final JTextField startTime = new JTextField();
    private final JTextField endTime = new JTextField();
    private final JLabel status = new JLabel();
    private final JButton submit = SwingTheme.primaryButton("Create Trip");

    public TripOptionsPanel(TripOptionsViewModel viewModel) {
        this(viewModel, null);
    }

    public TripOptionsPanel(
            TripOptionsViewModel viewModel, TripSetupController controller) {
        if (viewModel == null) {
            throw new IllegalArgumentException("Trip Options ViewModel is required");
        }
        this.viewModel = viewModel;
        this.controller = controller;
        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Trip Setup");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        heading.add(title);
        heading.add(Box.createVerticalStrut(4));
        JLabel notice = new JLabel(
                "Create a trip first; the same form edits the active trip later.");
        notice.setFont(SwingTheme.SMALL);
        notice.setForeground(SwingTheme.MUTED);
        heading.add(notice);
        add(heading, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, "Destination", destination);
        addField(fields, 1, "Date (YYYY-MM-DD)", date);
        addField(fields, 2, "Day starts (HH:MM)", startTime);
        addField(fields, 3, "Day ends (HH:MM)", endTime);
        add(fields, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        status.setFont(SwingTheme.SMALL);
        footer.add(status);
        footer.add(Box.createVerticalStrut(8));
        submit.setEnabled(controller != null);
        submit.addActionListener(event -> submit());
        footer.add(submit);
        add(footer, BorderLayout.SOUTH);

        renderState(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> {
            if ("feedback".equals(event.getPropertyName())) {
                renderFeedback(viewModel.getState());
            } else {
                renderState(viewModel.getState());
            }
        });
    }

    public JButton getSubmitButton() {
        return submit;
    }

    private void submit() {
        if (controller != null) {
            controller.execute(
                    destination.getText(),
                    date.getText(),
                    startTime.getText(),
                    endTime.getText());
        }
    }

    private void renderState(TripOptionsState state) {
        destination.setText(state.getDestination());
        date.setText(state.getDate() == null ? "" : state.getDate().toString());
        startTime.setText(
                state.getStartTime() == null ? "" : state.getStartTime().toString());
        endTime.setText(
                state.getEndTime() == null ? "" : state.getEndTime().toString());
        submit.setText(state.hasActiveTrip() ? "Save Trip Options" : "Create Trip");
        renderFeedback(state);
    }

    private void renderFeedback(TripOptionsState state) {
        status.setText(state.getMessage().isEmpty()
                ? "Enter trip details to begin." : state.getMessage());
        status.setForeground(state.isError() ? SwingTheme.ERROR : SwingTheme.SUCCESS);
    }

    private void addField(
            JPanel fields, int row, String label, java.awt.Component component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(6, 0, 6, 12);
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(SwingTheme.BODY);
        fieldLabel.setForeground(SwingTheme.NAVY);
        fields.add(fieldLabel, labelConstraints);

        GridBagConstraints valueConstraints = new GridBagConstraints();
        valueConstraints.gridx = 1;
        valueConstraints.gridy = row;
        valueConstraints.weightx = 1;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        valueConstraints.insets = new Insets(6, 0, 6, 0);
        fields.add(component, valueConstraints);
    }
}
