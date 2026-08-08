package trippy.application.ports;

import java.util.Optional;

/** Authentication port for durable per-user itinerary persistence. */
public interface AuthService {
    AuthSession signUp(String email, String password);

    AuthSession signIn(String email, String password);

    void signOut();

    Optional<AuthSession> currentSession();
}
