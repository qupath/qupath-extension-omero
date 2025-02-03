package qupath.ext.omero.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Utility methods for handling web requests.
 */
public class Utils {

    private Utils() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Returns the host part of a complex URI. For example,
     * {@code https://www.my-server.com/show=image-462} returns {@code https://www.my-server.com}.
     * This also adds the "https" scheme if none is already present in the provided URI.
     *
     * @return the host part of the URI
     * @throws URISyntaxException when the server URI could not have been created
     */
    public static URI getServerURI(URI uri) throws URISyntaxException {
        String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
        return new URI(String.format("%s://%s", scheme, uri.getAuthority()));
    }

    /**
     * Get a credential from a list of arguments.
     *
     * @param credentialLabel the text that indicates that the following argument is the credential to retrieve
     * @param args the list of arguments to iterate on
     * @return the credential present in the list of arguments, or an empty Optional if not found
     */
    public static Optional<String> getCredentialFromArgs(
            String credentialLabel,
            String... args
    ) {
        String credential = null;
        int i = 0;
        while (i < args.length-1) {
            String parameter = args[i++];
            if (credentialLabel.equals(parameter)) {
                credential = args[i++];
            }
        }

        return Optional.ofNullable(credential);
    }
}
