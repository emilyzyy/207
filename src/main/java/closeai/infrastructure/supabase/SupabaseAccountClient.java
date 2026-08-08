package closeai.infrastructure.supabase;

import closeai.application.ports.AccountService;
import closeai.application.ports.AuthService;
import closeai.application.ports.AuthSession;
import closeai.domain.entities.Friendship;
import closeai.domain.entities.TripParticipant;
import closeai.domain.entities.User;
import closeai.domain.valueobjects.TripAccessLevel;
import closeai.domain.valueobjects.TripAccessRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** PostgREST-backed profiles and friendships. */
public final class SupabaseAccountClient implements AccountService {
    private final String baseUrl;
    private final String anonKey;
    private final AuthService auth;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public SupabaseAccountClient(String baseUrl, String anonKey, AuthService auth) {
        this(baseUrl, anonKey, auth,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                new ObjectMapper());
    }

    public SupabaseAccountClient(
            String baseUrl, String anonKey, AuthService auth, HttpClient http, ObjectMapper mapper) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Supabase URL is required");
        }
        if (anonKey == null || anonKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Supabase anon key is required");
        }
        if (auth == null) {
            throw new IllegalArgumentException("Auth is required");
        }
        this.baseUrl = trimSlash(baseUrl.trim());
        this.anonKey = anonKey.trim();
        this.auth = auth;
        this.http = http == null ? HttpClient.newHttpClient() : http;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @Override
    public User ensureProfile(String preferredUsername) {
        AuthSession session = requireSession();
        Optional<User> existing = findById(session.getUserId());
        if (existing.isPresent()) {
            return existing.get();
        }
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            String username = chooseUsername(preferredUsername, session.getEmail(),
                    session.getUserId(), attempt);
            ObjectNode row = mapper.createObjectNode();
            row.put("id", session.getUserId());
            row.put("username", username);
            row.put("email", session.getEmail() == null ? "" : session.getEmail());
            row.put("avatar_color", User.DEFAULT_AVATAR_COLOR);
            row.putNull("avatar_image");
            row.put("updated_at", java.time.Instant.now().toString());
            try {
                request("POST", "/rest/v1/profiles?on_conflict=id", row.toString(),
                        "resolution=merge-duplicates,return=representation");
                return findById(session.getUserId()).orElseThrow(() ->
                        new IllegalStateException("Could not create your profile."));
            } catch (RuntimeException exception) {
                lastFailure = exception;
                String message = exception.getMessage() == null ? "" : exception.getMessage();
                // Username collision — try another generated name.
                if (message.toLowerCase(Locale.ROOT).contains("already taken")
                        || message.toLowerCase(Locale.ROOT).contains("duplicate")
                        || message.toLowerCase(Locale.ROOT).contains("unique")) {
                    preferredUsername = null;
                    continue;
                }
                throw exception;
            }
        }
        throw lastFailure == null
                ? new IllegalStateException("Could not create your profile.")
                : lastFailure;
    }

    @Override
    public Optional<User> currentProfile() {
        return auth.currentSession().flatMap(session -> findById(session.getUserId()));
    }

    @Override
    public User updateProfile(
            String username,
            String email,
            String password,
            String avatarColor,
            String avatarImage) {
        AuthSession session = requireSession();
        String cleanedUsername = requireValidUsername(username);
        if (!cleanedUsername.equalsIgnoreCase(
                currentProfile().map(User::getUsername).orElse(""))) {
            Optional<User> taken = findByUsername(cleanedUsername);
            if (taken.isPresent() && !taken.get().getId().equals(session.getUserId())) {
                throw new IllegalStateException("That username is already taken.");
            }
        }
        // Blank password means "keep current password" — do not re-submit it to Auth.
        String nextPassword = password == null ? "" : password;
        String currentEmail = session.getEmail() == null ? "" : session.getEmail();
        boolean emailChanged = email != null && !email.trim().equalsIgnoreCase(currentEmail);
        if (emailChanged || !nextPassword.isEmpty()) {
            auth.updateCredentials(email, nextPassword);
        }

        ObjectNode row = mapper.createObjectNode();
        row.put("id", session.getUserId());
        row.put("username", cleanedUsername);
        row.put("email", email == null ? "" : email.trim());
        row.put("avatar_color",
                avatarColor == null || avatarColor.trim().isEmpty()
                        ? User.DEFAULT_AVATAR_COLOR : avatarColor.trim());
        if (avatarImage == null || avatarImage.trim().isEmpty()) {
            row.putNull("avatar_image");
        } else {
            row.put("avatar_image", avatarImage.trim());
        }
        row.put("updated_at", java.time.Instant.now().toString());
        request("POST", "/rest/v1/profiles?on_conflict=id", row.toString(),
                "resolution=merge-duplicates,return=minimal");
        String savedColor = avatarColor == null || avatarColor.trim().isEmpty()
                ? User.DEFAULT_AVATAR_COLOR : avatarColor.trim();
        String savedImage = avatarImage == null || avatarImage.trim().isEmpty()
                ? null : avatarImage.trim();
        // Return the values just written so the corner avatar updates immediately.
        return new User(
                session.getUserId(),
                cleanedUsername,
                email == null ? "" : email.trim(),
                savedColor,
                savedImage);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        String body = request("GET",
                "/rest/v1/profiles?username=ilike." + enc(username.trim()) + "&select=*&limit=1",
                null, null);
        JsonNode array = readArray(body);
        if (!array.isArray() || array.size() == 0) {
            return Optional.empty();
        }
        return Optional.of(mapUser(array.get(0)));
    }

    @Override
    public Friendship sendFriendRequest(String username) {
        AuthSession session = requireSession();
        ensureProfile(null);
        User target = findByUsername(username).orElseThrow(() ->
                new IllegalStateException("No user found with that username."));
        if (target.getId().equals(session.getUserId())) {
            throw new IllegalStateException("You cannot friend yourself.");
        }
        if (alreadyConnected(session.getUserId(), target.getId())) {
            throw new IllegalStateException("You already have a request or friendship with that user.");
        }
        ObjectNode row = mapper.createObjectNode();
        row.put("requester_id", session.getUserId());
        row.put("addressee_id", target.getId());
        row.put("status", "pending");
        String body = request("POST", "/rest/v1/friendships", row.toString(),
                "return=representation");
        JsonNode array = readArray(body);
        if (!array.isArray() || array.size() == 0) {
            throw new IllegalStateException("Could not send the friend request.");
        }
        JsonNode created = array.get(0);
        return new Friendship(
                text(created, "id"),
                text(created, "requester_id"),
                text(created, "addressee_id"),
                Friendship.Status.PENDING,
                target);
    }

    @Override
    public void acceptFriendRequest(String friendshipId) {
        AuthSession session = requireSession();
        ObjectNode patch = mapper.createObjectNode();
        patch.put("status", "accepted");
        request("PATCH",
                "/rest/v1/friendships?id=eq." + enc(friendshipId)
                        + "&addressee_id=eq." + enc(session.getUserId())
                        + "&status=eq.pending",
                patch.toString(),
                "return=minimal");
    }

    @Override
    public void cancelFriendRequest(String friendshipId) {
        AuthSession session = requireSession();
        request("DELETE",
                "/rest/v1/friendships?id=eq." + enc(friendshipId)
                        + "&requester_id=eq." + enc(session.getUserId())
                        + "&status=eq.pending",
                null, null);
    }

    @Override
    public void removeFriend(String friendshipId) {
        AuthSession session = requireSession();
        request("DELETE",
                "/rest/v1/friendships?id=eq." + enc(friendshipId)
                        + "&or=(requester_id.eq." + enc(session.getUserId())
                        + ",addressee_id.eq." + enc(session.getUserId()) + ")"
                        + "&status=eq.accepted",
                null, null);
    }

    @Override
    public List<Friendship> listIncomingRequests() {
        AuthSession session = requireSession();
        String body = request("GET",
                "/rest/v1/friendships?addressee_id=eq." + enc(session.getUserId())
                        + "&status=eq.pending&select=*",
                null, null);
        return mapFriendships(body, session.getUserId(), true);
    }

    @Override
    public List<Friendship> listOutgoingRequests() {
        AuthSession session = requireSession();
        String body = request("GET",
                "/rest/v1/friendships?requester_id=eq." + enc(session.getUserId())
                        + "&status=eq.pending&select=*",
                null, null);
        return mapFriendships(body, session.getUserId(), true);
    }

    @Override
    public List<User> listFriends() {
        AuthSession session = requireSession();
        String body = request("GET",
                "/rest/v1/friendships?status=eq.accepted&or=(requester_id.eq."
                        + enc(session.getUserId()) + ",addressee_id.eq."
                        + enc(session.getUserId()) + ")&select=*",
                null, null);
        List<Friendship> rows = mapFriendships(body, session.getUserId(), false);
        List<User> friends = new ArrayList<>();
        for (Friendship friendship : rows) {
            friends.add(friendship.getOtherUser());
        }
        return friends;
    }

    @Override
    public void setTripMembers(String tripId, Map<String, TripAccessRole> memberRoles) {
        if (tripId == null || tripId.trim().isEmpty()) {
            throw new IllegalArgumentException("Trip id is required");
        }
        requireSession();
        String ownerId = getTripOwnerId(tripId.trim()).orElse(null);
        request("DELETE", "/rest/v1/trip_members?trip_id=eq." + enc(tripId.trim()), null, null);
        if (memberRoles == null || memberRoles.isEmpty()) {
            return;
        }
        ArrayNode rows = mapper.createArrayNode();
        for (Map.Entry<String, TripAccessRole> entry : memberRoles.entrySet()) {
            String memberId = entry.getKey();
            if (memberId == null || memberId.trim().isEmpty()) {
                continue;
            }
            String trimmed = memberId.trim();
            if (ownerId != null && trimmed.equals(ownerId)) {
                continue;
            }
            TripAccessRole role = entry.getValue() == null ? TripAccessRole.EDIT : entry.getValue();
            ObjectNode row = mapper.createObjectNode();
            row.put("trip_id", tripId.trim());
            row.put("user_id", trimmed);
            row.put("role", role.toDb());
            rows.add(row);
        }
        if (rows.size() > 0) {
            request("POST", "/rest/v1/trip_members", rows.toString(), "return=minimal");
        }
    }

    @Override
    public List<String> listTripCompanionUsernames(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return new ArrayList<>();
        }
        AuthSession session = requireSession();
        String body = request("GET",
                "/rest/v1/trip_members?trip_id=eq." + enc(tripId.trim()) + "&select=user_id,role",
                null, null);
        JsonNode array = readArray(body);
        List<String> usernames = new ArrayList<>();
        if (!array.isArray()) {
            return usernames;
        }
        for (JsonNode node : array) {
            String memberId = text(node, "user_id");
            if (memberId == null || memberId.equals(session.getUserId())) {
                continue;
            }
            findById(memberId).ifPresent(user -> usernames.add(user.getUsername()));
        }
        usernames.sort(String.CASE_INSENSITIVE_ORDER);
        return usernames;
    }

    @Override
    public List<TripParticipant> listTripParticipants(String tripId) {
        List<TripParticipant> participants = new ArrayList<>();
        if (tripId == null || tripId.trim().isEmpty()) {
            return participants;
        }
        requireSession();
        getTripOwner(tripId).ifPresent(owner -> participants.add(TripParticipant.owner(owner)));
        String body = request("GET",
                "/rest/v1/trip_members?trip_id=eq." + enc(tripId.trim()) + "&select=user_id,role",
                null, null);
        JsonNode array = readArray(body);
        if (!array.isArray()) {
            return participants;
        }
        List<TripParticipant> members = new ArrayList<>();
        for (JsonNode node : array) {
            String memberId = text(node, "user_id");
            if (memberId == null || memberId.isEmpty()) {
                continue;
            }
            TripAccessRole role = TripAccessRole.fromDb(text(node, "role"));
            findById(memberId).ifPresent(user ->
                    members.add(TripParticipant.member(user, role)));
        }
        members.sort((left, right) -> left.getUser().getUsername()
                .compareToIgnoreCase(right.getUser().getUsername()));
        participants.addAll(members);
        return participants;
    }

    @Override
    public TripAccessLevel getMyTripAccess(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return TripAccessLevel.NONE;
        }
        AuthSession session = requireSession();
        Optional<String> ownerId = getTripOwnerId(tripId.trim());
        if (!ownerId.isPresent()) {
            return TripAccessLevel.NONE;
        }
        if (ownerId.get().equals(session.getUserId())) {
            return TripAccessLevel.OWNER;
        }
        String body = request("GET",
                "/rest/v1/trip_members?trip_id=eq." + enc(tripId.trim())
                        + "&user_id=eq." + enc(session.getUserId())
                        + "&select=role&limit=1",
                null, null);
        JsonNode array = readArray(body);
        if (!array.isArray() || array.size() == 0) {
            return TripAccessLevel.NONE;
        }
        TripAccessRole role = TripAccessRole.fromDb(text(array.get(0), "role"));
        switch (role) {
            case ADMIN:
                return TripAccessLevel.ADMIN;
            case EDIT:
                return TripAccessLevel.EDIT;
            case VIEW:
            default:
                return TripAccessLevel.VIEW;
        }
    }

    @Override
    public Optional<User> getTripOwner(String tripId) {
        return getTripOwnerId(tripId).flatMap(this::findById);
    }

    private Optional<String> getTripOwnerId(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return Optional.empty();
        }
        String body = request("GET",
                "/rest/v1/trips?id=eq." + enc(tripId.trim()) + "&select=user_id&limit=1",
                null, null);
        JsonNode array = readArray(body);
        if (!array.isArray() || array.size() == 0) {
            return Optional.empty();
        }
        String ownerId = text(array.get(0), "user_id");
        if (ownerId == null || ownerId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ownerId);
    }

    private boolean alreadyConnected(String userId, String otherId) {
        String body = request("GET",
                "/rest/v1/friendships?or=("
                        + "and(requester_id.eq." + enc(userId) + ",addressee_id.eq." + enc(otherId) + "),"
                        + "and(requester_id.eq." + enc(otherId) + ",addressee_id.eq." + enc(userId) + ")"
                        + ")&select=id&limit=1",
                null, null);
        JsonNode array = readArray(body);
        return array.isArray() && array.size() > 0;
    }

    private List<Friendship> mapFriendships(String body, String currentUserId, boolean pendingOnly) {
        JsonNode array = readArray(body);
        List<Friendship> result = new ArrayList<>();
        if (!array.isArray()) {
            return result;
        }
        for (JsonNode node : array) {
            String requesterId = text(node, "requester_id");
            String addresseeId = text(node, "addressee_id");
            String otherId = currentUserId.equals(requesterId) ? addresseeId : requesterId;
            Optional<User> other = findById(otherId);
            if (!other.isPresent()) {
                continue;
            }
            Friendship.Status status = "accepted".equalsIgnoreCase(text(node, "status"))
                    ? Friendship.Status.ACCEPTED : Friendship.Status.PENDING;
            if (pendingOnly && status != Friendship.Status.PENDING) {
                continue;
            }
            result.add(new Friendship(
                    text(node, "id"), requesterId, addresseeId, status, other.get()));
        }
        return result;
    }

    private Optional<User> findById(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Optional.empty();
        }
        String body = request("GET",
                "/rest/v1/profiles?id=eq." + enc(userId) + "&select=*&limit=1",
                null, null);
        JsonNode array = readArray(body);
        if (!array.isArray() || array.size() == 0) {
            return Optional.empty();
        }
        return Optional.of(mapUser(array.get(0)));
    }

    private User mapUser(JsonNode node) {
        String image = text(node, "avatar_image");
        return new User(
                text(node, "id"),
                text(node, "username"),
                text(node, "email"),
                text(node, "avatar_color"),
                image);
    }

    private String chooseUsername(String preferred, String email, String userId, int attempt) {
        if (attempt == 0 && preferred != null && !preferred.trim().isEmpty()) {
            String cleaned = sanitizeUsername(preferred.trim());
            if (isValidUsername(cleaned)) {
                return cleaned;
            }
        }
        String base = sanitizeUsername(emailPrefix(email));
        if (base.length() < 3) {
            base = "user";
        }
        if (base.length() > 12) {
            base = base.substring(0, 12);
        }
        String idPart = userId == null ? "" : userId.replace("-", "");
        String suffix;
        if (attempt <= 0 && idPart.length() >= 6) {
            suffix = idPart.substring(0, 6);
        } else if (idPart.length() >= 4) {
            suffix = idPart.substring(0, 4)
                    + Integer.toString(ThreadLocalRandom.current().nextInt(1000, 9999));
        } else {
            suffix = Integer.toString(ThreadLocalRandom.current().nextInt(100000, 999999));
        }
        String candidate = base + suffix;
        if (candidate.length() > 24) {
            candidate = candidate.substring(0, 24);
        }
        if (!isValidUsername(candidate)) {
            candidate = ("user" + suffix);
            if (candidate.length() > 24) {
                candidate = candidate.substring(0, 24);
            }
        }
        return candidate;
    }

    private static String emailPrefix(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "user";
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String sanitizeUsername(String raw) {
        String cleaned = raw.replaceAll("[^a-zA-Z0-9_]", "").toLowerCase(Locale.ROOT);
        if (cleaned.length() > 24) {
            cleaned = cleaned.substring(0, 24);
        }
        return cleaned;
    }

    private static String requireValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        String cleaned = username.trim();
        if (!isValidUsername(cleaned)) {
            throw new IllegalArgumentException(
                    "Username must be 3–24 characters: letters, numbers, or underscore.");
        }
        return cleaned;
    }

    private static boolean isValidUsername(String username) {
        return username != null
                && username.length() >= 3
                && username.length() <= 24
                && username.matches("^[a-zA-Z0-9_]+$");
    }

    private AuthSession requireSession() {
        return auth.currentSession().orElseThrow(() ->
                new IllegalStateException("Sign in before using your account."));
    }

    private String request(String method, String path, String jsonBody, String prefer) {
        try {
            AuthSession session = requireSession();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer " + session.getAccessToken())
                    .header("Content-Type", "application/json");
            if (prefer != null && !prefer.isEmpty()) {
                builder.header("Prefer", prefer);
            }
            if ("GET".equals(method)) {
                builder.GET();
            } else if ("DELETE".equals(method)) {
                builder.DELETE();
            } else if ("POST".equals(method)) {
                builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
            } else if ("PATCH".equals(method)) {
                builder.method("PATCH",
                        HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
            } else {
                throw new IllegalArgumentException("Unsupported method: " + method);
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(friendlyError(response.statusCode(), response.body()));
            }
            return response.body() == null ? "" : response.body();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Account request failed: " + exception.getMessage(),
                    exception);
        }
    }

    private String friendlyError(int status, String body) {
        String lower = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (status == 404 || lower.contains("pgrst205") || lower.contains("schema cache")
                || (lower.contains("could not find") && lower.contains("profiles"))) {
            return "Profiles table not found. In Supabase → SQL Editor, run docs/supabase/schema.sql "
                    + "(the profiles and friendships section), then try again.";
        }
        if (status == 409 || lower.contains("duplicate") || lower.contains("unique")) {
            return "That username is already taken.";
        }
        if (body != null && body.length() < 180 && !body.contains("{")) {
            return body;
        }
        return "Account request failed (HTTP " + status + ").";
    }

    private JsonNode readArray(String body) {
        try {
            if (body == null || body.trim().isEmpty()) {
                return mapper.createArrayNode();
            }
            JsonNode node = mapper.readTree(body);
            if (node.isArray()) {
                return node;
            }
            return mapper.createArrayNode().add(node);
        } catch (IOException exception) {
            throw new IllegalStateException("Invalid account JSON: " + exception.getMessage(),
                    exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
