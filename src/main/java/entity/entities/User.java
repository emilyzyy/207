package entity.entities;

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
    /**
     * Hex color used when no uploaded image is set (default white).
     * @return the result of the operation
     */

    public String getAvatarColor() {
        return avatarColor;
    }
    /**
     * Optional base64-encoded image bytes; null means use {@link #getAvatarColor()}.
     * @return the result of the operation
     */

    public String getAvatarImage() {
        return avatarImage;
    }

    /**
     * Performs the h as up lo ad ed av at ar operation.
     * @return the result of the operation
     */
    public boolean hasUploadedAvatar() {
        return avatarImage != null && !avatarImage.isEmpty();
    }

    /**
     * Performs the w it hu se rn am e operation.
     * @param newUsername the n ew us er na me value
     * @return the result of the operation
     */
    public User withUsername(String newUsername) {
        return new User(id, newUsername, email, avatarColor, avatarImage);
    }

    /**
     * Performs the w it he ma il operation.
     * @param newEmail the n ew em ai l value
     * @return the result of the operation
     */
    public User withEmail(String newEmail) {
        return new User(id, username, newEmail, avatarColor, avatarImage);
    }

    /**
     * Performs the w it ha va ta r operation.
     * @param image the i ma ge value
     * @param color the c ol or value
     * @return the result of the operation
     */
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
