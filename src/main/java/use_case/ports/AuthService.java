package use_case.ports;

import java.util.Optional;

/** Authentication port for durable per-user itinerary persistence. */
public interface AuthService {
    AuthSession signUp(String email, String password);

    AuthSession signIn(String email, String password);

    /**
     * Updates the signed-in user's email and/or password via the auth provider.
     * Blank password leaves the current password unchanged.
     */
    AuthSession updateCredentials(String email, String password);

    void signOut();

    Optional<AuthSession> currentSession();
}
