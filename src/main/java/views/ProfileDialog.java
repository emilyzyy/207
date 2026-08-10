package views;

import entity.entities.User;
import interface_adapter.controllers.ProfileController;
import interface_adapter.viewmodels.ProfileState;
import interface_adapter.viewmodels.ProfileViewModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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

/** Profile View: delegates load / save / sign-out to {@link ProfileController}. */
public final class ProfileDialog extends JDialog {
    private final ProfileController controller;
    private final ProfileViewModel viewModel;
    private final String sessionPassword;
    private final JTextField usernameField = new JTextField(24);
    private final JTextField emailField = new JTextField(24);
    private final JPasswordField oldPasswordField = new JPasswordField(24);
    private final JPasswordField newPasswordField = new JPasswordField(24);
    private final JPasswordField confirmPasswordField = new JPasswordField(24);
    private final JPanel passwordChangePanel = new JPanel(new GridBagLayout());
    private final JButton changePasswordButton = SwingTheme.secondaryButton("Change your password");
    private final JLabel avatarPreview = new JLabel();
    private final JLabel status = new JLabel(" ");
    private String avatarColor;
    private String avatarImage;
    private boolean changingPassword;
    private boolean saved;
    private boolean signOutRequested;
    private User savedProfile;

    public ProfileDialog(
            JFrame owner,
            ProfileController controller,
            ProfileViewModel viewModel,
            String sessionPassword) {
        super(owner, "Profile", true);
        if (controller == null || viewModel == null) {
            throw new IllegalArgumentException("Profile controller and ViewModel are required");
        }
        this.controller = controller;
        this.viewModel = viewModel;
        this.sessionPassword = sessionPassword == null ? "" : sessionPassword;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        User initial = viewModel.getState().getProfile();
        if (initial == null) {
            controller.load();
            initial = viewModel.getState().getProfile();
        }
        if (initial == null) {
            throw new IllegalStateException(
                    viewModel.getState().getMessage().isEmpty()
                            ? "Could not load profile."
                            : viewModel.getState().getMessage());
        }
        avatarColor = initial.getAvatarColor();
        avatarImage = initial.getAvatarImage();
        usernameField.setText(initial.getUsername());
        emailField.setText(initial.getEmail());
        refreshAvatarPreview();

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        root.setBackground(SwingTheme.PANEL);

        JLabel title = new JLabel("Your profile");
        title.setFont(SwingTheme.HEADING);
        title.setForeground(SwingTheme.NAVY);
        root.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel avatarRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        avatarRow.setOpaque(false);
        avatarPreview.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        avatarRow.add(avatarPreview);
        JButton changeAvatar = SwingTheme.secondaryButton("Change picture");
        changeAvatar.addActionListener(event -> chooseAvatar());
        avatarRow.add(changeAvatar);
        avatarRow.setAlignmentX(LEFT_ALIGNMENT);
        center.add(avatarRow);
        center.add(Box.createVerticalStrut(12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints gc = new GridBagConstraints();
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

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JButton signOut = SwingTheme.secondaryButton("Sign out");
        signOut.addActionListener(event -> {
            controller.signOut();
            if (viewModel.getState().isSignedOut()) {
                signOutRequested = true;
                dispose();
            }
        });
        footer.add(signOut, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JButton save = SwingTheme.primaryButton("Save");
        save.addActionListener(event -> {
            controller.save(
                    usernameField.getText(),
                    emailField.getText(),
                    avatarColor,
                    avatarImage,
                    changingPassword,
                    new String(oldPasswordField.getPassword()),
                    new String(newPasswordField.getPassword()),
                    new String(confirmPasswordField.getPassword()),
                    sessionPassword);
            ProfileState state = viewModel.getState();
            status.setText(state.getMessage().isEmpty() ? " " : state.getMessage());
            status.setForeground(state.isError() ? SwingTheme.ERROR : SwingTheme.SUCCESS);
            if (state.isSaved() && state.getProfile() != null) {
                saved = true;
                savedProfile = state.getProfile();
                dispose();
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
        GridBagConstraints gc = new GridBagConstraints();
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
        String[] options = {"Solid colour", "Upload photo", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Choose how to set your profile picture.",
                "Profile picture",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);
        if (choice == 0) {
            Color chosen = pickSolidColor();
            if (chosen != null) {
                avatarColor = AvatarSupport.toHex(chosen);
                avatarImage = null;
                refreshAvatarPreview();
            }
        } else if (choice == 1) {
            try {
                String encoded = AvatarSupport.chooseImageBase64(this);
                if (encoded != null) {
                    avatarImage = encoded;
                    refreshAvatarPreview();
                }
            } catch (RuntimeException exception) {
                status.setForeground(SwingTheme.ERROR);
                status.setText(exception.getMessage());
            }
        }
    }

    private Color pickSolidColor() {
        JPanel swatches = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        final Color[] selected = {null};
        JDialog picker = new JDialog(this, "Choose a colour", true);
        for (Color color : AvatarSupport.SOLID_COLORS) {
            JButton swatch = new JButton();
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
}
