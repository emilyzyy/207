package closeai.adapters.views;

import closeai.adapters.controllers.TripSetupController;
import closeai.adapters.viewmodels.TripOptionsState;
import closeai.adapters.viewmodels.TripOptionsViewModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
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
    private final CardLayout destinationLayout = new CardLayout();
    private final JPanel destinationValue = new JPanel(destinationLayout);
    private final JLabel destinationDisplay = new JLabel();
    private final DateSelectionButton date = new DateSelectionButton(java.time.LocalDate.now());
    private final TimeSelectorPanel startTime =
            new TimeSelectorPanel(java.time.LocalTime.of(9, 0));
    private final TimeSelectorPanel endTime =
            new TimeSelectorPanel(java.time.LocalTime.of(18, 0));
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
        destinationValue.setOpaque(false);
        destinationDisplay.setFont(SwingTheme.BODY.deriveFont(java.awt.Font.BOLD));
        destinationDisplay.setForeground(SwingTheme.NAVY);
        destinationValue.add(destination, "editable");
        destinationValue.add(destinationDisplay, "display");
        addField(fields, 0, "Destination", destinationValue);
        addField(fields, 1, "Date (YYYY-MM-DD)", date);
        addField(fields, 2, "Day starts (HH:MM)", startTime);
        addField(fields, 3, "Day ends (HH:MM)", endTime);

        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 1;
        buttonConstraints.gridy = 4;
        buttonConstraints.anchor = GridBagConstraints.WEST;
        buttonConstraints.insets = new Insets(8, 0, 0, 0);
        submit.setEnabled(controller != null);
        submit.addActionListener(event -> submit());
        fields.add(submit, buttonConstraints);

        GridBagConstraints statusConstraints = new GridBagConstraints();
        statusConstraints.gridx = 1;
        statusConstraints.gridy = 5;
        statusConstraints.anchor = GridBagConstraints.WEST;
        statusConstraints.insets = new Insets(8, 0, 0, 0);
        status.setFont(SwingTheme.SMALL);
        fields.add(status, statusConstraints);
        add(fields, BorderLayout.CENTER);

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
            String submittedDestination = viewModel.getState().hasActiveTrip()
                    ? viewModel.getState().getDestination() : destination.getText();
            controller.execute(
                    submittedDestination,
                    date.getDate().toString(),
                    startTime.getTime().toString(),
                    endTime.getTime().toString());
        }
    }

    private void renderState(TripOptionsState state) {
        destination.setText(state.getDestination());
        destinationDisplay.setText(state.getDestination());
        destination.setEditable(!state.hasActiveTrip());
        destination.setFocusable(!state.hasActiveTrip());
        destination.setToolTipText(state.hasActiveTrip()
                ? "Destination is fixed for this day plan" : "Enter the trip destination");
        destinationDisplay.setToolTipText("Destination is fixed for this day plan");
        destinationLayout.show(destinationValue,
                state.hasActiveTrip() ? "display" : "editable");
        date.setDate(state.getDate());
        startTime.setTime(state.getStartTime());
        endTime.setTime(state.getEndTime());
        submit.setText(state.hasActiveTrip() ? "Save Trip Options" : "Create Trip");
        renderFeedback(state);
    }

    private void renderFeedback(TripOptionsState state) {
        status.setText(state.getMessage());
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
