package trippy.adapters.views;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.time.LocalDate;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Button that opens the same calendar widget used by New Itinerary. */
public final class DateSelectionButton extends JButton {
    private LocalDate date;

    public DateSelectionButton(LocalDate initial) {
        super();
        setDate(initial == null ? LocalDate.now() : initial);
        addActionListener(event -> showCalendar());
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate value) {
        date = value == null ? LocalDate.now() : value;
        setText(date.toString());
    }

    private void showCalendar() {
        DatePickerPanel picker = new DatePickerPanel(date);
        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this), "Select trip date",
                Dialog.ModalityType.APPLICATION_MODAL);
        JButton cancel = SwingTheme.secondaryButton("Cancel");
        cancel.addActionListener(event -> dialog.dispose());
        JButton select = SwingTheme.primaryButton("Select Date");
        select.addActionListener(event -> {
            setDate(picker.getDate());
            dialog.dispose();
        });
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        actions.add(cancel);
        actions.add(select);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(picker, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(select);
        dialog.pack();
        dialog.setLocationRelativeTo((Component) this);
        dialog.setVisible(true);
    }
}
