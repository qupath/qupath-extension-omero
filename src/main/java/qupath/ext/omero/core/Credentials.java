package qupath.ext.omero.core;

import java.util.Objects;

/**
 * A class to store authentication information.
 *
 * @param userType the type of (and whether there is) authentication
 * @param username the username to use when authenticating. Can be null if no authentication should be performed
 * @param password the password to use when authenticating. Can be null if no authentication should be performed
 */
public record Credentials(UserType userType, String username, char[] password) {

    /**
     * The type of (and whether there is) authentication.
     */
    public enum UserType {
        /**
         * The public user. This doesn't require authentication
         */
        PUBLIC_USER,
        /**
         * A regular user. This requires authentication.
         */
        REGULAR_USER
    }

    /**
     * Create a public user.
     */
    public Credentials() {
        this(UserType.PUBLIC_USER, null, null);
    }

    /**
     * Create a regular user.
     *
     * @param username the username of the user
     * @param password the password of the user. It should be cleared once the authentication is performed
     */
    public Credentials(String username, char[] password) {
        this(UserType.REGULAR_USER, username, password);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Credentials that = (Credentials) object;
        return userType == that.userType && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userType, username);
    }

    @Override
    public String toString() {
        return switch (userType) {
            case PUBLIC_USER -> "Public user";
            case REGULAR_USER -> String.format("User with username '%s'", username);
        };
    }
}
