package trippy.domain.entities;

/** Signed-in account profile (username, contact, and avatar). */
public final class User {
    public static final String DEFAULT_AVATAR_COLOR = "#FFFFFF";

    private final String id;
    private final String username;
    private final String email;
    private final String avatarColor;
    private final String avatarImage;

    public User(String id, String username, String email) {
        this(id, username, email, DEFAULT_AVATAR_COLOR, null);
    }

    public User(String id, String username, String email, String avatarColor, String avatarImage) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("User id is required");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        this.id = id.trim();
        this.username = username.trim();
        this.email = email == null ? "" : email.trim();
        this.avatarColor = normalizeColor(avatarColor);
        this.avatarImage = blankToNull(avatarImage);
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    /** Hex color used when no uploaded image is set (default white). */
    public String getAvatarColor() {
        return avatarColor;
    }

    /** Optional base64-encoded image bytes; null means use {@link #getAvatarColor()}. */
    public String getAvatarImage() {
        return avatarImage;
    }

    public boolean hasUploadedAvatar() {
        return avatarImage != null && !avatarImage.isEmpty();
    }

    public User withUsername(String newUsername) {
        return new User(id, newUsername, email, avatarColor, avatarImage);
    }

    public User withEmail(String newEmail) {
        return new User(id, username, newEmail, avatarColor, avatarImage);
    }

    public User withAvatar(String color, String image) {
        return new User(id, username, email, color, image);
    }

    private static String normalizeColor(String color) {
        if (color == null || color.trim().isEmpty()) {
            return DEFAULT_AVATAR_COLOR;
        }
        String trimmed = color.trim();
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }
        return trimmed.toUpperCase();
    }

    private static String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
