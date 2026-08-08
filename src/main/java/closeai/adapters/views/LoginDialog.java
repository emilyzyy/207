package closeai.adapters.views;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Modal email/password login and signup for Supabase-backed persistence. */
public final class LoginDialog extends JDialog {
    private final JTextField emailField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JTextField usernameField = new JTextField(24);
    private final JLabel usernameLabel = new JLabel("Username (optional)");
    private final JLabel status = new JLabel(" ");
    private boolean confirmed;
    private boolean signUp;

    public LoginDialog(JFrame owner) {
        super(owner, "CloseAI account", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        root.setBackground(SwingTheme.PANEL);

        JLabel title = new JLabel("Sign in to save and reopen itineraries");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
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
        gc.gridx = 0;
        gc.gridy = 2;
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

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(status, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> {
            confirmed = false;
            dispose();
        });
        JButton create = SwingTheme.primaryButton("Create account");
        create.addActionListener(event -> {
            signUp = true;
            confirmed = true;
            dispose();
        });
        JButton signIn = SwingTheme.primaryButton("Sign in");
        signIn.addActionListener(event -> {
            signUp = false;
            confirmed = true;
            dispose();
        });
        buttons.add(cancel);
        buttons.add(create);
        buttons.add(signIn);
        footer.add(buttons, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public boolean isSignUp() {
        return signUp;
    }

    public String getEmail() {
        return emailField.getText().trim();
    }

    public String getPassword() {
        return new String(passwordField.getPassword());
    }

    /** Optional username used only when creating an account. */
    public String getUsername() {
        return usernameField.getText().trim();
    }

    public void showError(String message) {
        status.setText(message == null ? "Unable to authenticate" : message);
        status.setForeground(SwingTheme.ERROR);
    }
}
