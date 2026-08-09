package views;

import java.awt.Component;
import javax.swing.JOptionPane;

/** Consistent safeguards and feedback for destructive presentation actions. */
final class RemovalDialogs {
    private RemovalDialogs() {
    }

    static boolean confirm(Component parent, String title, String message) {
        Object[] options = {"Cancel", "Remove"};
        int answer = JOptionPane.showOptionDialog(
                parent,
                message,
                title,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);
        return answer == 1;
    }

    static void notifyRemoved(Component parent, String message) {
        JOptionPane.showMessageDialog(
                parent, message, "Removal complete", JOptionPane.INFORMATION_MESSAGE);
    }
}
