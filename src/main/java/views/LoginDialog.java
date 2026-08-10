package views;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Modal email/password login and signup as two switchable pages. */
public final class LoginDialog extends JDialog {
    private final JTextField emailField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JPasswordField confirmPasswordField = new JPasswordField(24);
    private final JTextField usernameField = new JTextField(24);
    private final JLabel titleLabel = new JLabel();
    private final JLabel confirmLabel = new JLabel("Confirm password");
    private final JLabel usernameLabel = new JLabel("Username (optional)");
    private final JLabel status = new JLabel(" ");
    private final JButton primaryButton = SwingTheme.primaryButton("Sign in");
    private final JPanel switchRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
    private final JLabel switchPrompt = new JLabel();
    private final JLabel switchLink = new JLabel();
    private boolean confirmed;
    private boolean signUpMode;

    public LoginDialog(JFrame owner) {
        super(owner, "Trippy account", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        final JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        root.setBackground(SwingTheme.PANEL);

        titleLabel.setFont(SwingTheme.HEADING);
        titleLabel.setForeground(SwingTheme.NAVY);
        root.add(titleLabel, BorderLayout.NORTH);

        final JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        final GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(4, 0, 4, 8);
        form.add(new JLabel("Email"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        form.add(emailField, gc);

        gc.gridx = 0;
        gc.gridy = 1;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        form.add(new JLabel("Password"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        form.add(passwordField, gc);
        passwordField.setToolTipText("At least " + PasswordRules.MIN_LENGTH + " characters");

        gc.gridx = 0;
        gc.gridy = 2;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        form.add(confirmLabel, gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        form.add(confirmPasswordField, gc);

        gc.gridx = 0;
        gc.gridy = 3;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        form.add(usernameLabel, gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        form.add(usernameField, gc);
        root.add(form, BorderLayout.CENTER);

        status.setForeground(SwingTheme.ERROR);
        status.setFont(SwingTheme.SMALL);

        final JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        footer.add(status, BorderLayout.NORTH);

        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        final JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> {
            confirmed = false;
            dispose();
        });
        primaryButton.addActionListener(event -> submit());
        actions.add(cancel);
        actions.add(primaryButton);
        footer.add(actions, BorderLayout.CENTER);

        switchRow.setOpaque(false);
        switchPrompt.setFont(SwingTheme.SMALL);
        switchPrompt.setForeground(SwingTheme.MUTED);
        switchLink.setFont(SwingTheme.SMALL.deriveFont(java.awt.Font.BOLD));
        switchLink.setForeground(SwingTheme.BLUE);
        switchLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        switchLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showMode(!signUpMode);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                switchLink.setForeground(SwingTheme.NAVY);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                switchLink.setForeground(SwingTheme.BLUE);
            }
        });
        switchRow.add(switchPrompt);
        switchRow.add(switchLink);
        footer.add(switchRow, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        showMode(false);
        setLocationRelativeTo(owner);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public boolean isSignUp() {
        return signUpMode;
    }

    public String getEmail() {
        return emailField.getText().trim();
    }

    public String getPassword() {
        return new String(passwordField.getPassword());
    }

    public String getConfirmPassword() {
        return new String(confirmPasswordField.getPassword());
    }
    /**
     * Optional username used only when creating an account.
     * @return the result of the operation
     */

    public String getUsername() {
        return usernameField.getText().trim();
    }

    /**
     * Performs the s ho we rr or operation.
     * @param message the m es sa ge value
     */
    public void showError(String message) {
        status.setText(message == null ? "Unable to authenticate" : message);
        status.setForeground(SwingTheme.ERROR);
    }

    private void submit() {
        if (getEmail().isEmpty()) {
            showError("Please enter your email.");
            return;
        }
        if (signUpMode) {
            final String passwordError = PasswordRules.validateNewPasswordPair(
                    getPassword(), getConfirmPassword());
            if (passwordError != null) {
                showError(passwordError);
                return;
            }
        }
        else if (getPassword().isEmpty()) {
            showError("Please enter your password.");
            return;
        }
        confirmed = true;
        dispose();
    }

    private void showMode(boolean signUp) {
        this.signUpMode = signUp;
        status.setText(" ");
        if (signUp) {
            setTitle("Create account");
            titleLabel.setText("Create your CloseAI account");
            primaryButton.setText("Create account");
            confirmLabel.setVisible(true);
            confirmPasswordField.setVisible(true);
            usernameLabel.setVisible(true);
            usernameField.setVisible(true);
            switchPrompt.setText("Already have an account?");
            switchLink.setText("Sign in");
        }
        else {
            setTitle("Sign in");
            titleLabel.setText("Sign in");
            primaryButton.setText("Sign in");
            confirmLabel.setVisible(false);
            confirmPasswordField.setVisible(false);
            confirmPasswordField.setText("");
            usernameLabel.setVisible(false);
            usernameField.setVisible(false);
            switchPrompt.setText("Don't have an account?");
            switchLink.setText("Create an account");
        }
        pack();
    }
}
