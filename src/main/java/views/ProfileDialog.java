package views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import entity.entities.User;

/** Editable profile: avatar, username, email, optional password change, with save and sign out. */
public final class ProfileDialog extends JDialog {
    private final JTextField usernameField = new JTextField(24);
    private final JTextField emailField = new JTextField(24);
    private final JPasswordField oldPasswordField = new JPasswordField(24);
    private final JPasswordField newPasswordField = new JPasswordField(24);
    private final JPasswordField confirmPasswordField = new JPasswordField(24);
    private final JPanel passwordChangePanel = new JPanel(new GridBagLayout());
    private final JButton changePasswordButton = SwingTheme.secondaryButton("Change your password");
    private final JLabel avatarPreview = new JLabel();
    private final JLabel status = new JLabel(" ");
    private final String currentPassword;
    private String avatarColor;
    private String avatarImage;
    private boolean changingPassword;
    private boolean saved;
    private boolean signOutRequested;
    private User savedProfile;

    public ProfileDialog(
            JFrame owner,
            User profile,
            String currentPassword,
            Function<ProfileSaveRequest, User> onSave) {
        super(owner, "Profile", true);
        if (profile == null || onSave == null) {
            throw new IllegalArgumentException("Profile and save handler are required");
        }
        this.currentPassword = currentPassword == null ? "" : currentPassword;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        avatarColor = profile.getAvatarColor();
        avatarImage = profile.getAvatarImage();
        usernameField.setText(profile.getUsername());
        emailField.setText(profile.getEmail());
        refreshAvatarPreview();

        final JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        root.setBackground(SwingTheme.PANEL);

        final JLabel title = new JLabel("Your profile");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        root.add(title, BorderLayout.NORTH);

        final JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        final JPanel avatarRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        avatarRow.setOpaque(false);
        avatarPreview.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        avatarRow.add(avatarPreview);
        final JButton changeAvatar = SwingTheme.secondaryButton("Change picture");
        changeAvatar.addActionListener(event -> chooseAvatar());
        avatarRow.add(changeAvatar);
        avatarRow.setAlignmentX(LEFT_ALIGNMENT);
        center.add(avatarRow);
        center.add(Box.createVerticalStrut(12));

        final JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setAlignmentX(LEFT_ALIGNMENT);
        final GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(4, 0, 4, 8);
        form.add(new JLabel("Username"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        form.add(usernameField, gc);

        gc.gridx = 0;
        gc.gridy = 1;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        form.add(new JLabel("Email"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        form.add(emailField, gc);
        center.add(form);
        center.add(Box.createVerticalStrut(10));

        changePasswordButton.setAlignmentX(LEFT_ALIGNMENT);
        changePasswordButton.addActionListener(event -> togglePasswordChange());
        center.add(changePasswordButton);
        center.add(Box.createVerticalStrut(8));

        buildPasswordChangePanel();
        passwordChangePanel.setVisible(false);
        passwordChangePanel.setAlignmentX(LEFT_ALIGNMENT);
        center.add(passwordChangePanel);
        center.add(Box.createVerticalStrut(8));

        status.setFont(SwingTheme.SMALL);
        status.setAlignmentX(LEFT_ALIGNMENT);
        center.add(status);
        root.add(center, BorderLayout.CENTER);

        final JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        final JButton signOut = SwingTheme.secondaryButton("Sign out");
        signOut.addActionListener(event -> {
            signOutRequested = true;
            dispose();
        });
        footer.add(signOut, BorderLayout.WEST);
        final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        final JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        final JButton save = SwingTheme.primaryButton("Save");
        save.addActionListener(event -> {
            try {
                String newPassword = "";
                if (changingPassword) {
                    final String oldPassword = new String(oldPasswordField.getPassword());
                    final String nextPassword = new String(newPasswordField.getPassword());
                    final String confirmPassword = new String(confirmPasswordField.getPassword());
                    if (oldPassword.isEmpty() && nextPassword.isEmpty() && confirmPassword.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Enter your old and new passwords, or cancel password change.");
                    }
                    if (!oldPassword.equals(this.currentPassword)) {
                        throw new IllegalArgumentException("Current password is incorrect.");
                    }
                    final String passwordError = PasswordRules.validateNewPasswordPair(
                            nextPassword, confirmPassword);
                    if (passwordError != null) {
                        throw new IllegalArgumentException(passwordError);
                    }
                    newPassword = nextPassword;
                }
                final ProfileSaveRequest request = new ProfileSaveRequest(
                        usernameField.getText().trim(),
                        emailField.getText().trim(),
                        newPassword,
                        avatarColor,
                        avatarImage);
                savedProfile = onSave.apply(request);
                saved = true;
                status.setForeground(SwingTheme.SUCCESS);
                status.setText("Profile saved.");
                dispose();
            }
            catch (RuntimeException exception) {
                status.setForeground(SwingTheme.ERROR);
                status.setText(exception.getMessage() == null
                        ? "Could not save profile." : exception.getMessage());
            }
        });
        right.add(cancel);
        right.add(save);
        footer.add(right, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setMinimumSize(getPreferredSize());
        setLocationRelativeTo(owner);
    }

    public boolean isSaved() {
        return saved;
    }

    public User getSavedProfile() {
        return savedProfile;
    }

    public boolean isSignOutRequested() {
        return signOutRequested;
    }

    private void buildPasswordChangePanel() {
        passwordChangePanel.setOpaque(false);
        final GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(4, 0, 4, 8);
        passwordChangePanel.add(new JLabel("Current password"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        passwordChangePanel.add(oldPasswordField, gc);

        gc.gridx = 0;
        gc.gridy = 1;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        passwordChangePanel.add(new JLabel("New password"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        passwordChangePanel.add(newPasswordField, gc);

        gc.gridx = 0;
        gc.gridy = 2;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        passwordChangePanel.add(new JLabel("Confirm new password"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        passwordChangePanel.add(confirmPasswordField, gc);
    }

    private void togglePasswordChange() {
        changingPassword = !changingPassword;
        passwordChangePanel.setVisible(changingPassword);
        changePasswordButton.setText(changingPassword
                ? "Cancel password change"
                : "Change your password");
        if (!changingPassword) {
            oldPasswordField.setText("");
            newPasswordField.setText("");
            confirmPasswordField.setText("");
        }
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void chooseAvatar() {
        final String[] options = {"Solid colour", "Upload photo", "Cancel"};
        final int choice = JOptionPane.showOptionDialog(
                this,
                "Choose how to set your profile picture.",
                "Profile picture",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);
        if (choice == 0) {
            final Color chosen = pickSolidColor();
            if (chosen != null) {
                avatarColor = AvatarSupport.toHex(chosen);
                avatarImage = null;
                refreshAvatarPreview();
            }
        }
        else if (choice == 1) {
            try {
                final String encoded = AvatarSupport.chooseImageBase64(this);
                if (encoded != null) {
                    avatarImage = encoded;
                    refreshAvatarPreview();
                }
            }
            catch (RuntimeException exception) {
                status.setForeground(SwingTheme.ERROR);
                status.setText(exception.getMessage());
            }
        }
    }

    private Color pickSolidColor() {
        final JPanel swatches = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        final Color[] selected = {null};
        final JDialog picker = new JDialog(this, "Choose a colour", true);
        for (Color color : AvatarSupport.SOLID_COLORS) {
            final JButton swatch = new JButton();
            swatch.setPreferredSize(new java.awt.Dimension(36, 36));
            swatch.setBackground(color);
            swatch.setOpaque(true);
            swatch.setBorder(BorderFactory.createLineBorder(SwingTheme.LINE));
            swatch.addActionListener(event -> {
                selected[0] = color;
                picker.dispose();
            });
            swatches.add(swatch);
        }
        picker.setContentPane(swatches);
        picker.pack();
        picker.setLocationRelativeTo(this);
        picker.setVisible(true);
        return selected[0];
    }

    private void refreshAvatarPreview() {
        avatarPreview.setIcon(AvatarSupport.iconFor(avatarColor, avatarImage, 64));
    }
    /** Values collected from the profile form on Save. */

    public static final class ProfileSaveRequest {
        private final String username;
        private final String email;
        private final String password;
        private final String avatarColor;
        private final String avatarImage;

        public ProfileSaveRequest(
                String username,
                String email,
                String password,
                String avatarColor,
                String avatarImage) {
            this.username = username;
            this.email = email;
            this.password = password;
            this.avatarColor = avatarColor;
            this.avatarImage = avatarImage;
        }

        public String getUsername() {
            return username;
        }

        public String getEmail() {
            return email;
        }
        /**
         * Empty when the password is not being changed.
         * @return the result of the operation
         */

        public String getPassword() {
            return password;
        }

        public String getAvatarColor() {
            return avatarColor;
        }

        public String getAvatarImage() {
            return avatarImage;
        }
    }
}
