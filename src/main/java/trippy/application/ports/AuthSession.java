package trippy.application.ports;

/** Immutable signed-in session used by Supabase-backed persistence. */
public final class AuthSession {
    private final String userId;
    private final String accessToken;
    private final String email;

    public AuthSession(String userId, String accessToken, String email) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User id is required");
        }
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Access token is required");
        }
        this.userId = userId.trim();
        this.accessToken = accessToken.trim();
        this.email = email == null ? "" : email.trim();
    }

    public String getUserId() {
        return userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getEmail() {
        return email;
    }
}
