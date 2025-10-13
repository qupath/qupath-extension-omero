package qupath.ext.omero.core.apis.json.serverinformation;

import java.util.Objects;

/**
 * Represents a CSRF token, which is required for any POST, PUT and DELETE requests.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param data the CSRF token. Required
 */
public record Token(String data) {
    public Token {
        Objects.requireNonNull(data);
    }
}
