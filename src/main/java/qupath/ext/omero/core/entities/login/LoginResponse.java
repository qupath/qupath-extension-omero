package qupath.ext.omero.core.entities.login;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import omero.IllegalArgumentException;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.lib.io.GsonTools;

/**
 * Reads the response from a login request.
 */
public class LoginResponse {

    private final Status status;
    private final Group group;
    private final long userId;
    private final String sessionUuid;

    /**
     * The login status
     */
    public enum Status {

        /**
         * The authentication was cancelled by the user
         */
        CANCELED,
        /**
         * The guest account (without authentication) is used
         */
        UNAUTHENTICATED,
        /**
         * The authentication succeeded and the user is logged in
         */
        AUTHENTICATED
    }

    private LoginResponse(Status status, Group group, long userId, String sessionUuid) {
        this.status = status;
        this.group = group;
        this.userId = userId;
        this.sessionUuid = sessionUuid;
    }

    private LoginResponse(Status status) {
        this(status, null, -1, null);
    }

    @Override
    public String toString() {
        return String.format("LoginResponse of status %s for user of ID %d", status, userId);
    }

    /**
     * Create a new login response with an unauthenticated or canceled status.
     * It is not possible to call this function with a successful login.
     *
     * @param status the status of the login
     * @return a response with the given status
     * @throws IllegalArgumentException when {@code status} is {@link Status#AUTHENTICATED}
     */
    public static LoginResponse createNonAuthenticatedLoginResponse(Status status) {
        if (status.equals(Status.AUTHENTICATED)) {
            throw new IllegalArgumentException("You cannot create a non successful login response with a success status");
        }
        return new LoginResponse(status);
    }

    /**
     * Parse and read a server response.
     *
     * @param serverResponse the raw server response of the login request
     * @return an authenticated LoginResponse
     * @throws IllegalArgumentException when the server response doesn't contain the required elements
     */
    public static LoginResponse createAuthenticatedLoginResponse(JsonObject serverResponse) {
        if (!serverResponse.has("eventContext") || !serverResponse.get("eventContext").isJsonObject()) {
            throw new IllegalArgumentException(String.format(
                    "'eventContext' JSON object not found in %s", serverResponse
            ));
        }
        JsonObject eventContext = serverResponse.get("eventContext").getAsJsonObject();

        if (!eventContext.has("userId") || !eventContext.get("userId").isJsonPrimitive() || !eventContext.getAsJsonPrimitive("userId").isNumber()) {
            throw new IllegalArgumentException(String.format(
                    "'userId' number not found in %s", eventContext
            ));
        }
        if (!eventContext.has("sessionUuid") || !eventContext.get("sessionUuid").isJsonPrimitive()) {
            throw new IllegalArgumentException(String.format(
                    "'sessionUuid' text not found in %s", eventContext
            ));
        }

        Group group;
        try {
            group = GsonTools.getInstance().fromJson(eventContext, Group.class);
        } catch (JsonSyntaxException e) {
            throw new java.lang.IllegalArgumentException(e);
        }

        return new LoginResponse(
                Status.AUTHENTICATED,
                group,
                eventContext.get("userId").getAsJsonPrimitive().getAsNumber().intValue(),
                eventContext.get("sessionUuid").getAsString()
        );
    }

    /**
     * @return the authentication status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return the user ID of the authenticated user, or -1 if no authentication
     * was performed
     */
    public long getUserId() {
        return userId;
    }

    /**
     * @return the session UUID of the authenticated user, or null if no authentication
     * was performed
     */
    public String getSessionUuid() {
        return sessionUuid;
    }

    /**
     * @return the group of the authenticated user, or null if no authentication
     * was performed
     */
    public Group getGroup() {
        return group;
    }
}
