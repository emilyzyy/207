package closeai.adapters.views;

import closeai.adapters.viewmodels.TripOptionsState;
import closeai.adapters.viewmodels.TripOptionsViewModel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Read-only milestone view of the seeded trip options. */
public final class TripOptionsPanel extends JPanel {
    private final TripOptionsViewModel viewModel;
    private final JPanel fields = new JPanel(new GridBagLayout());

    public TripOptionsPanel(TripOptionsViewModel viewModel) {
        this.viewModel = viewModel;
        setLayout(new BorderLayout(0, 12));
        setBackground(SwingTheme.PANEL);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Trip Options");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        heading.add(title);
        heading.add(Box.createVerticalStrut(4));
        JLabel notice = new JLabel(
                "Seeded demo values · editing is not wired for this milestone");
        notice.setFont(SwingTheme.SMALL);
        notice.setForeground(SwingTheme.MUTED);
        heading.add(notice);
        add(heading, BorderLayout.NORTH);

        fields.setOpaque(false);
        add(fields, BorderLayout.CENTER);

        JPanel footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        footer.setOpaque(false);
        footer.add(SwingTheme.placeholderButton("Save options (not wired)"));
        add(footer, BorderLayout.SOUTH);

        render(viewModel.getState());
        viewModel.addPropertyChangeListener(event -> render(viewModel.getState()));
    }

    private void render(TripOptionsState state) {
        fields.removeAll();
        addField(0, "Destination", state.getDestination());
        addField(1, "Date", String.valueOf(state.getDate()));
        addField(2, "Day starts", String.valueOf(state.getStartTime()));
        addField(3, "Day ends", String.valueOf(state.getEndTime()));
        addField(4, "Transportation", String.valueOf(state.getTransportationMode()));
        fields.revalidate();
        fields.repaint();
    }

    private void addField(int row, String label, String value) {
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
        JTextField display = new JTextField(value == null ? "" : value);
        display.setEditable(false);
        display.setBackground(SwingTheme.BACKGROUND);
        fields.add(display, valueConstraints);
    }
}
