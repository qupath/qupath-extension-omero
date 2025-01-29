package qupath.ext.omero.core;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * <p>
 *     Utility classes that monitors all active connections to servers.
 * </p>
 * <p>
 *     {@link WebClient Webclients} should be created and removed from this class.
 * </p>
 */
public class WebClients {

    private static final Logger logger = LoggerFactory.getLogger(WebClients.class);
    private static final ObservableList<WebClient> clients = FXCollections.observableArrayList();
    private static final ObservableList<WebClient> clientsImmutable = FXCollections.unmodifiableObservableList(clients);
    private static final Set<URI> clientsBeingCreated = new HashSet<>();
    public static class ClientAlreadyExistingException extends RuntimeException {}

    private WebClients() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * <p>
     *     Create a WebClient from the server URI provided. Basically, this function will
     *     call {@link WebClient#create(URI, WebClient.Authentication, String...)}  WebClient.create()}
     *     and internally stores the newly created client.
     * </p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the client creation failed).
     * </p>
     *
     * @param url the URL of the server. It doesn't have to be the base URL of the server
     * @param authentication how to handle authentication
     * @param args optional arguments to login. See {@link WebClient#create(URI, WebClient.Authentication, String...) WebClient.create()}
     * @return a CompletableFuture (that may complete exceptionally) with the client. The returned client will be null if the user cancelled
     * the client creation
     */
    public static CompletableFuture<WebClient> createClient(String url, WebClient.Authentication authentication, String... args) {
        URI serverURI;
        try {
            serverURI = WebUtilities.getServerURI(new URI(url));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return getClientFromURI(serverURI)
                .map(CompletableFuture::completedFuture)
                .orElseGet(() -> {
                    synchronized (WebClients.class) {
                        if (clientsBeingCreated.contains(serverURI)) {
                            return CompletableFuture.failedFuture(new ClientAlreadyExistingException());
                        } else {
                            clientsBeingCreated.add(serverURI);

                            return WebClient.create(serverURI, authentication, args)
                                    .thenApply(client -> {
                                        if (client != null) {
                                            PreferencesManager.addURI(client.getApisHandler().getWebServerURI());
                                            PreferencesManager.setEnableUnauthenticated(client.getApisHandler().getWebServerURI(), switch (authentication) {
                                                case ENFORCE -> false;
                                                case TRY_TO_SKIP, SKIP -> true;
                                            });

                                            synchronized (WebClients.class) {
                                                clients.add(client);
                                            }
                                        }
                                        return client;
                                    })
                                    .whenComplete((client, error) -> {
                                        synchronized (WebClients.class) {
                                            clientsBeingCreated.remove(serverURI);
                                        }
                                    });
                    }
            }
        });
    }

    /**
     * Synchronous version of {@link #createClient(String, WebClient.Authentication, String...)} that calls
     * {@link WebClient#createSync(URI, WebClient.Authentication, String...)}.
     * @throws Exception when the client creation fails
     */
    public static WebClient createClientSync(String url, WebClient.Authentication authentication, String... args) throws Exception {
        URI serverURI = WebUtilities.getServerURI(new URI(url));

        Optional<WebClient> existingClient = getClientFromURI(serverURI);
        if (existingClient.isPresent()) {
            return existingClient.get();
        }

        synchronized (WebClients.class) {
            if (clientsBeingCreated.contains(serverURI)) {
                throw new ClientAlreadyExistingException();
            } else {
                clientsBeingCreated.add(serverURI);
            }
        }

        try {
            WebClient client = WebClient.createSync(serverURI, authentication, args);
            if (client != null) {
                PreferencesManager.addURI(client.getApisHandler().getWebServerURI());
                PreferencesManager.setEnableUnauthenticated(client.getApisHandler().getWebServerURI(), switch (authentication) {
                    case ENFORCE -> false;
                    case TRY_TO_SKIP, SKIP -> true;
                });

                synchronized (WebClients.class) {
                    clients.add(client);
                }
            }

            return client;
        } finally {
            synchronized (WebClients.class) {
                clientsBeingCreated.remove(serverURI);
            }
        }
    }

    /**
     * Retrieve the client corresponding to the provided uri.
     *
     * @param uri the web server URI of the client to retrieve
     * @return the client corresponding to the URI, or an empty Optional if not found
     */
    public static synchronized Optional<WebClient> getClientFromURI(URI uri) {
        return clients.stream().filter(client -> client.getApisHandler().getWebServerURI().equals(uri)).findAny();
    }

    /**
     * Close the given client connection.
     * This may take a moment as an HTTP request is made.
     *
     * @param client the client to remove
     */
    public static void removeClient(WebClient client) {
        try {
            client.close();
        } catch (Exception e) {
            logger.error("Error when closing web client", e);
        }

        synchronized(WebClients.class) {
            clients.remove(client);
        }
    }

    /**
     * <p>Returns an unmodifiable list of all connected (but not necessarily authenticated) clients.</p>
     * <p>This list may be updated from any thread.</p>
     *
     * @return the connected clients
     */
    public static ObservableList<WebClient> getClients() {
        return clientsImmutable;
    }
}
