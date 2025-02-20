package qupath.ext.omero.core.imageserver;

import qupath.ext.omero.core.Credentials;

import java.net.URI;

/**
 * A static class to prompt for credentials using the command line.
 */
class CommandLineAuthenticator {

    private CommandLineAuthenticator() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Prompt the user for credentials using the command line.
     *
     * @param webServerUri the web server URI to connect to. This will be used to indicate the user the server
     *                     to give credentials for
     * @param username an optional username. If null, this function will ask for both a username and a password. If not
     *                 null, this function will only ask for a password
     * @return the credentials given by the user
     * @throws IllegalStateException if the console is inaccessible
     */
    public static Credentials authenticate(URI webServerUri, String username) {
        if (System.console() == null) {
            throw new IllegalStateException("Can't prompt user for credentials because the console is inaccessible");
        }

        System.out.printf("Please provide the credentials to connect to %s%n", webServerUri);

        if (username == null) {
            System.out.print("Username (empty if the public user should be used): ");
            username = System.console().readLine();
        } else {
            System.out.printf("Username: %s%n", username);
        }

        char[] password = null;
        if (username != null && !username.isBlank()) {
            System.out.print("Password: ");
            password = System.console().readPassword();
        }

        if (username != null && !username.isBlank() && password != null) {
            return new Credentials(username, password);
        } else {
            return new Credentials();
        }
    }
}
