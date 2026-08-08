package closeai.infrastructure.supabase;

import closeai.application.ports.AuthService;
import closeai.application.ports.AuthSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/** Supabase GoTrue auth client (email/password) for desktop persistence. */
public final class SupabaseAuthClient implements AuthService {
    private final String baseUrl;
    private final String anonKey;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private volatile AuthSession session;

    public SupabaseAuthClient(String baseUrl, String anonKey) {
        this(baseUrl, anonKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                new ObjectMapper());
    }

    public SupabaseAuthClient(String baseUrl, String anonKey, HttpClient http, ObjectMapper mapper) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Supabase URL is required");
        }
        if (anonKey == null || anonKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Supabase anon key is required");
        }
        this.baseUrl = trimSlash(baseUrl.trim());
        this.anonKey = anonKey.trim();
        this.http = http == null ? HttpClient.newHttpClient() : http;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @Override
    public AuthSession signUp(String email, String password) {
        requireCredentials(email, password);
        JsonNode body = postJson(baseUrl + "/auth/v1/signup",
                "{\"email\":" + quote(email.trim()) + ",\"password\":" + quote(password) + "}",
                true);
        AuthSession created = sessionFrom(body, email.trim(), password);
        this.session = created;
        return created;
    }

    @Override
    public AuthSession signIn(String email, String password) {
        requireCredentials(email, password);
        JsonNode body = postJson(baseUrl + "/auth/v1/token?grant_type=password",
                "{\"email\":" + quote(email.trim()) + ",\"password\":" + quote(password) + "}",
                false);
        AuthSession created = sessionFrom(body, email.trim(), password);
        this.session = created;
        return created;
    }

    @Override
    public void signOut() {
        this.session = null;
    }

    @Override
    public AuthSession updateCredentials(String email, String password) {
        AuthSession current = session;
        if (current == null) {
            throw new IllegalStateException("Sign in before updating your account.");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter your email.");
        }
        String trimmedEmail = email.trim();
        String nextPassword = password == null ? "" : password;
        // Skip Auth round-trip when nothing credential-related changed.
        boolean emailChanged = !trimmedEmail.equalsIgnoreCase(
                current.getEmail() == null ? "" : current.getEmail());
        boolean passwordChanged = !nextPassword.isEmpty()
                && !nextPassword.equals(current.getPassword());
        if (!emailChanged && !passwordChanged) {
            return current;
        }
        StringBuilder json = new StringBuilder("{\"email\":").append(quote(trimmedEmail));
        String retainedPassword = current.getPassword();
        if (passwordChanged) {
            json.append(",\"password\":").append(quote(nextPassword));
            retainedPassword = nextPassword;
        }
        json.append("}");
        JsonNode body = putAuthJson(baseUrl + "/auth/v1/user", json.toString(), current.getAccessToken());
        String newEmail = text(body, "email");
        if (newEmail == null || newEmail.isEmpty()) {
            JsonNode user = body.get("user");
            if (user != null && !user.isNull()) {
                newEmail = text(user, "email");
            }
        }
        if (newEmail == null || newEmail.isEmpty()) {
            newEmail = trimmedEmail;
        }
        AuthSession updated = new AuthSession(
                current.getUserId(), current.getAccessToken(), newEmail, retainedPassword);
        this.session = updated;
        return updated;
    }

    @Override
    public Optional<AuthSession> currentSession() {
        return Optional.ofNullable(session);
    }

    private AuthSession sessionFrom(JsonNode body, String fallbackEmail, String password) {
        String accessToken = text(body, "access_token");
        JsonNode user = body.get("user");
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IllegalStateException(
                    "Sign-in did not complete. If you just signed up, confirm your email "
                            + "or disable email confirmation in Supabase for demos.");
        }
        if (user == null || user.isNull()) {
            throw new IllegalStateException("Supabase auth response missing user");
        }
        String userId = text(user, "id");
        String email = text(user, "email");
        if (email == null || email.isEmpty()) {
            email = fallbackEmail;
        }
        return new AuthSession(userId, accessToken, email, password);
    }

    private JsonNode putAuthJson(String url, String jsonBody, String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(friendlyAuthError(response.body(), false));
            }
            return mapper.readTree(response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Could not update account credentials. Check your connection.",
                    exception);
        }
    }

    private JsonNode postJson(String url, String jsonBody, boolean signUp) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer " + anonKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(friendlyAuthError(response.body(), signUp));
            }
            return mapper.readTree(response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Could not reach the sign-in service. Check your connection.",
                    exception);
        }
    }

    private String friendlyAuthError(String body, boolean signUp) {
        String code = null;
        String message = null;
        try {
            if (body != null && !body.trim().isEmpty()) {
                JsonNode node = mapper.readTree(body);
                code = authErrorCode(node);
                message = firstNonBlankText(node, "msg", "message", "error_description", "error");
            }
        } catch (IOException ignored) {
            // Fall through to generic mapping.
        }

        String normalizedCode = code == null ? "" : code.trim().toLowerCase();
        String normalizedMessage = message == null ? "" : message.trim().toLowerCase();

        if (looksLike(normalizedCode, normalizedMessage,
                "user_already_exists", "already registered", "already been registered",
                "email_exists", "user already registered")) {
            return "That email is already registered. Try signing in instead.";
        }
        if (looksLike(normalizedCode, normalizedMessage,
                "invalid_credentials", "invalid login credentials", "invalid email or password")) {
            return "Incorrect email or password. Please try again.";
        }
        if (looksLike(normalizedCode, normalizedMessage,
                "email_address_invalid", "invalid email", "unable to validate email")) {
            return "Please enter a valid email address.";
        }
        if (looksLike(normalizedCode, normalizedMessage,
                "weak_password", "password should be", "password is too short")) {
            return "Password is too weak. Use at least 6 characters.";
        }
        if (looksLike(normalizedCode, normalizedMessage,
                "email_not_confirmed", "email not confirmed", "confirm your email")) {
            return "Please confirm your email before signing in, or disable email confirmation in Supabase for demos.";
        }
        if (looksLike(normalizedCode, normalizedMessage,
                "over_request_rate_limit", "rate limit", "too many requests")) {
            return "Too many attempts. Please wait a moment and try again.";
        }
        if (looksLike(normalizedCode, normalizedMessage, "validation_failed")) {
            return signUp
                    ? "Please check your email and password, then try creating the account again."
                    : "Please check your email and password, then try signing in again.";
        }
        if (message != null && !message.trim().isEmpty() && message.trim().length() < 160
                && !message.contains("{")) {
            return message.trim();
        }
        return signUp
                ? "Could not create the account. Please try again."
                : "Could not sign in. Please try again.";
    }

    private static String authErrorCode(JsonNode node) {
        String errorCode = text(node, "error_code");
        if (notBlank(errorCode)) {
            return errorCode;
        }
        String code = text(node, "code");
        if (notBlank(code) && !code.matches("\\d+")) {
            return code;
        }
        return null;
    }

    private static String firstNonBlankText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (notBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean looksLike(String code, String message, String... needles) {
        for (String needle : needles) {
            if ((!code.isEmpty() && code.contains(needle))
                    || (!message.isEmpty() && message.contains(needle))) {
                return true;
            }
        }
        return false;
    }

    private static void requireCredentials(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter your email.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Please enter your password.");
        }
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
