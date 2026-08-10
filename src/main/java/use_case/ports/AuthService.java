package use_case.ports;

import java.util.Optional;

/** Authentication port for durable per-user itinerary persistence. */
public interface AuthService {
    /**
     * Performs the s ig nu p operation.
     * @param password the p as sw or d value
     * @param email the e ma il value
     * @return the result of the operation
     */
    AuthSession signUp(String email, String password);

    /**
     * Performs the s ig ni n operation.
     * @param password the p as sw or d value
     * @param email the e ma il value
     * @return the result of the operation
     */
    AuthSession signIn(String email, String password);

    /**
     * Updates the signed-in user's email and/or password via the auth provider.
     * Blank password leaves the current password unchanged.
      * @param password the p as sw or d value
      * @param email the e ma il value
      * @return the result of the operation
     */
    AuthSession updateCredentials(String email, String password);

    /** Performs the s ig no ut operation. */
    void signOut();

    /**
     * Performs the c ur re nt se ss io n operation.
     * @return the result of the operation
     */
    Optional<AuthSession> currentSession();
}
