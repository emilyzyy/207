package closeai.adapters.views;

import closeai.domain.entities.User;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;
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

/** Editable profile: avatar, username, email, password, with save and sign out. */
public final class ProfileDialog extends JDialog {
    private final JTextField usernameField = new JTextField(24);
    private final JTextField emailField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JLabel avatarPreview = new JLabel();
    private final JLabel status = new JLabel(" ");
    private String avatarColor;
    private String avatarImage;
    private boolean saved;
    private boolean signOutRequested;

    public ProfileDialog(
            JFrame owner,
            User profile,
            String currentPassword,
            Consumer<ProfileSaveRequest> onSave) {
        super(owner, "Profile", true);
        if (profile == null || onSave == null) {
            throw new IllegalArgumentException("Profile and save handler are required");
        }
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        avatarColor = profile.getAvatarColor();
        avatarImage = profile.getAvatarImage();
        usernameField.setText(profile.getUsername());
        emailField.setText(profile.getEmail());
        passwordField.setText(currentPassword == null ? "" : currentPassword);
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

        gc.gridx = 0;
        gc.gridy = 2;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        form.add(new JLabel("Password"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        form.add(passwordField, gc);
        center.add(form);
        center.add(Box.createVerticalStrut(8));

        status.setFont(SwingTheme.SMALL);
        status.setAlignmentX(LEFT_ALIGNMENT);
        center.add(status);
        root.add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JButton signOut = SwingTheme.secondaryButton("Sign out");
        signOut.addActionListener(event -> {
            signOutRequested = true;
            dispose();
        });
        footer.add(signOut, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JButton save = SwingTheme.primaryButton("Save");
        save.addActionListener(event -> {
            try {
                ProfileSaveRequest request = new ProfileSaveRequest(
                        usernameField.getText().trim(),
                        emailField.getText().trim(),
                        new String(passwordField.getPassword()),
                        avatarColor,
                        avatarImage);
                onSave.accept(request);
                saved = true;
                status.setForeground(SwingTheme.SUCCESS);
                status.setText("Profile saved.");
                dispose();
            } catch (RuntimeException exception) {
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

    public boolean isSignOutRequested() {
        return signOutRequested;
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
