package qupath.ext.omero.core;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.entities.login.LoginResponse;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.lib.gui.QuPathGUI;
import qupath.ext.omero.core.entities.repositoryentities.Server;
import qupath.ext.omero.core.pixelapis.ice.IceAPI;
import qupath.ext.omero.core.pixelapis.PixelAPI;
import qupath.ext.omero.core.pixelapis.web.WebAPI;
import qupath.ext.omero.core.pixelapis.mspixelbuffer.MsPixelBufferAPI;
import qupath.lib.gui.viewer.QuPathViewer;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *     Class representing an OMERO Web Client. It handles creating a connection with an OMERO server
 *     and keeping the connection alive.
 * </p>
 * <p>
 *     A client can be connected to a server without being authenticated if the server allows it.
 * </p>
 * <p>
 *     One client corresponds to one user, which means a new client must be created to switch
 *     user.
 * </p>
 * <p>
 *     It has a reference to a {@link ApisHandler}
 *     which can be used to retrieve information from the OMERO server,
 *     and a reference to a {@link Server Server}
 *     which is the ancestor of all OMERO entities.
 * </p>
 * <p>
 *     A client must be {@link #close() closed} once no longer used.
 *     This is handled by {@link WebClients#removeClient(WebClient)}.
 * </p>
 * <p>
 *     This class is thread-safe.
 * </p>
 */
public class WebClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(WebClient.class);
    private static final int PING_DELAY_SECONDS = 60;
    private final ObservableList<PixelAPI> availablePixelAPIs = FXCollections.observableArrayList();
    private final ObservableList<PixelAPI> availablePixelAPIsImmutable = FXCollections.unmodifiableObservableList(availablePixelAPIs);
    private final ObjectProperty<PixelAPI> selectedPixelAPI = new SimpleObjectProperty<>();
    private final ObservableSet<URI> openedImagesURIs = FXCollections.observableSet();
    private final ObservableSet<URI> openedImagesURIsImmutable = FXCollections.unmodifiableObservableSet(openedImagesURIs);
    private final ScheduledExecutorService pingScheduler = Executors.newScheduledThreadPool(1);
    private final Server server;
    private final ApisHandler apisHandler;
    private final String username;
    private final boolean authenticated;
    private final List<PixelAPI> allPixelAPIs;

    /**
     * How to handle authentication when creation a connection
     */
    public enum Authentication {
        /**
         * Enforce authentication
         */
        ENFORCE,
        /**
         * Try to skip authentication if the server allows it
         */
        TRY_TO_SKIP,
        /**
         * Skip authentication even if the server doesn't allow it. In this case,
         * the client creation should fail.
         */
        SKIP
    }

    private WebClient(
            Server server,
            ApisHandler apisHandler,
            String username,
            boolean authenticated,
            List<PixelAPI> allPixelAPIs
    ) {
        this.server = server;
        this.apisHandler = apisHandler;
        this.username = username;
        this.authenticated = authenticated;
        this.allPixelAPIs = allPixelAPIs;

        if (authenticated) {
            pingScheduler.scheduleAtFixedRate(
                    () -> apisHandler.ping().exceptionally(error -> {
                        logger.error("Ping failed. Removing client", error);
                        WebClients.removeClient(WebClient.this);
                        return null;
                    }),
                    0,
                    PING_DELAY_SECONDS,
                    TimeUnit.SECONDS
            );
        }

        setUpPixelAPIs();

        logger.info(String.format(
                "Connected to the OMERO.web instance at %s with %s",
                apisHandler.getWebServerURI(),
                authenticated ? String.format("user %s", username) : "unauthenticated user"
        ));
    }

    /**
     * <p>
     *     Static factory method creating a new client. It will initialize the connection and
     *     ask for credentials if this is required to access the server.
     * </p>
     * <p>
     *     This function should only be used by {@link WebClients WebClients}
     *     which monitors opened clients (see {@link WebClients#createClient(String, Authentication, String...)}).
     * </p>
     * <p>The optional arguments must have one of the following format:</p>
     * <ul>
     *     <li>{@code --username [username] --password [password]}</li>
     *     <li>{@code -u [username] -p [password]}</li>
     * </ul>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if a request failed for example).
     * </p>
     *
     * @param uri the server URI to connect to
     * @param authentication how to handle authentication
     * @param args optional arguments to authenticate (see description above)
     * @return a CompletableFuture (that may complete exceptionally) with the client. The returned client will be null if the user cancelled
     * the client creation
     */
    static CompletableFuture<WebClient> create(URI uri, Authentication authentication, String... args) {
        return ApisHandler.create(uri).thenCompose(apisHandler ->
                authenticateAndCreateClient(apisHandler, uri, authentication, args)
        );
    }

    @Override
    public void close() throws Exception {
        logger.info(String.format("Disconnected from the OMERO.web instance at %s", apisHandler.getWebServerURI()));

        synchronized (this) {
            for (PixelAPI pixelAPI: allPixelAPIs) {
                pixelAPI.close();
            }
        }
        pingScheduler.close();
        apisHandler.close();
    }

    @Override
    public String toString() {
        return String.format("""
                Web client of:
                    username: %s
                    authenticated: %s
                    selectedPixelAPI: %s
                """, username, authenticated, selectedPixelAPI.get());
    }

    /**
     * @return the {@link ApisHandler} of this client
     */
    public ApisHandler getApisHandler() {
        return apisHandler;
    }

    /**
     * @return the {@link Server Server} of this client
     */
    public Server getServer() {
        return server;
    }

    /**
     * @return a whether the client is authenticated
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * @return the username of the authenticated user, or the username of
     * the public user if unauthenticated
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return the currently selected pixel API. This property may be updated from any thread
     * and may be null
     */
    public ReadOnlyObjectProperty<PixelAPI> getSelectedPixelAPI() {
        return selectedPixelAPI;
    }

    /**
     * Set the currently selected pixel API of this client.
     *
     * @param pixelAPI  the pixel API to select
     * @throws IllegalArgumentException when the provided pixel API is not available
     * or not part of the pixel APIs of this client
     */
    public void setSelectedPixelAPI(PixelAPI pixelAPI) {
        if (!pixelAPI.isAvailable().get()) {
            throw new IllegalArgumentException("The provided pixel API is not available");
        }
        if (!allPixelAPIs.contains(pixelAPI)) {
            throw new IllegalArgumentException("The provided pixel API is not part of the pixel APIs of this client");
        }

        synchronized (this) {
            selectedPixelAPI.set(pixelAPI);
        }
    }

    /**
     * @return an immutable observable list of all pixel APIs available for this client.
     * This set may be updated from any thread
     */
    public ObservableList<PixelAPI> getAvailablePixelAPIs() {
        return availablePixelAPIsImmutable;
    }

    /**
     * Return the pixel API corresponding to the class passed in parameter.
     * This pixel API is not guaranteed to be available.
     *
     * @param pixelAPIClass the class of the pixel API to retrieve
     * @return the pixel API corresponding to the class passed in parameter
     * @param <T>  the class of the pixel API to retrieve
     * @throws IllegalArgumentException if the pixel API was not found
     */
    public synchronized <T extends PixelAPI> T getPixelAPI(Class<T> pixelAPIClass) {
        return allPixelAPIs.stream()
                .filter(pixelAPIClass::isInstance)
                .map(pixelAPIClass::cast)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("The pixel API was not found"));
    }

    /**
     * <p>
     *     Returns a set of image URIs of this server which have been opened in this session.
     *     This class does not automatically detect if new images are opened, so this function
     *     actually only returns the URIs given to {@link #addOpenedImage(URI) addOpenedImage}.
     * </p>
     * <p>This function returns an unmodifiable list, use {@link #addOpenedImage(URI) addOpenedImage} to update its state.</p>
     * <p>This list may be updated from any thread.</p>
     *
     * @return a set of image URIs
     */
    public ObservableSet<URI> getOpenedImagesURIs() {
        return openedImagesURIsImmutable;
    }

    /**
     * Add an image URI to the list of currently opened images given by
     * {@link #getOpenedImagesURIs() getOpenedImagesURIs}.
     *
     * @param imageURI  the image URI
     */
    public synchronized void addOpenedImage(URI imageURI) {
        openedImagesURIs.add(imageURI);
    }

    /**
     * Indicates if this client can be closed, by checking if there is any
     * opened image in the QuPath viewer that belongs to this client.
     *
     * @return whether this client can be closed
     */
    public boolean canBeClosed() {
        return !(QuPathGUI.getInstance() != null && QuPathGUI.getInstance().getAllViewers().stream()
                .map(QuPathViewer::getServer)
                .anyMatch(server -> server instanceof OmeroImageServer omeroImageServer && omeroImageServer.getClient().equals(this)));
    }

    private void setUpPixelAPIs() {
        availablePixelAPIs.setAll(allPixelAPIs.stream()
                .filter(pixelAPI -> pixelAPI.isAvailable().get())
                .toList()
        );
        for (PixelAPI pixelAPI: allPixelAPIs) {
            pixelAPI.isAvailable().addListener((p, o, n) -> {
                synchronized (this) {
                    if (n && !availablePixelAPIs.contains(pixelAPI)) {
                        availablePixelAPIs.add(pixelAPI);
                    } else {
                        availablePixelAPIs.remove(pixelAPI);
                    }
                }
            });
        }

        selectedPixelAPI.set(availablePixelAPIs.stream()
                .filter(PixelAPI::canAccessRawPixels)
                .findAny()
                .orElse(availablePixelAPIs.get(0))
        );
        availablePixelAPIs.addListener((ListChangeListener<? super PixelAPI>) change -> {
            synchronized (this) {
                selectedPixelAPI.set(availablePixelAPIs.stream()
                        .filter(PixelAPI::canAccessRawPixels)
                        .findAny()
                        .orElse(availablePixelAPIs.get(0)));
            }
        });
    }

    private static CompletableFuture<WebClient> authenticateAndCreateClient(ApisHandler apisHandler, URI uri, Authentication authentication, String... args) {
        return authenticate(apisHandler, uri, authentication, args)
                .thenApplyAsync(loginResponse -> switch (loginResponse.getStatus()) {
                    case CANCELED -> null;
                    case UNAUTHENTICATED, AUTHENTICATED -> {
                        Server server = loginResponse.getStatus().equals(LoginResponse.Status.AUTHENTICATED) ?
                                    Server.create(apisHandler, loginResponse.getGroup(), loginResponse.getUserId()).join() :
                                    Server.create(apisHandler).join();

                        yield new WebClient(
                                server,
                                apisHandler,
                                server.getConnectedOwner().username(),
                                loginResponse.getStatus().equals(LoginResponse.Status.AUTHENTICATED),
                                List.of(
                                        new WebAPI(apisHandler),
                                        new IceAPI(apisHandler, loginResponse.getStatus().equals(LoginResponse.Status.AUTHENTICATED), loginResponse.getSessionUuid()),
                                        new MsPixelBufferAPI(apisHandler)
                                )
                        );
                    }
                });
    }

    private static CompletableFuture<LoginResponse> authenticate(ApisHandler apisHandler, URI uri, Authentication authentication, String... args) {
        String usernameFromArgs = getCredentialFromArgs("--username", "-u", args).orElse(null);
        String passwordFromArgs = getCredentialFromArgs("--password", "-p", args).orElse(null);

        return switch (authentication) {
            case ENFORCE -> apisHandler.login(usernameFromArgs, passwordFromArgs);
            case TRY_TO_SKIP -> {
                if (apisHandler.canSkipAuthentication()) {
                    yield CompletableFuture.completedFuture(LoginResponse.createNonAuthenticatedLoginResponse(LoginResponse.Status.UNAUTHENTICATED));
                } else {
                    yield apisHandler.login(usernameFromArgs, passwordFromArgs);
                }
            }
            case SKIP -> {
                if (apisHandler.canSkipAuthentication()) {
                    yield CompletableFuture.completedFuture(LoginResponse.createNonAuthenticatedLoginResponse(LoginResponse.Status.UNAUTHENTICATED));
                } else {
                    throw new IllegalArgumentException(String.format("The server %s doesn't allow browsing without being authenticated", uri));
                }
            }
        };
    }

    private static Optional<String> getCredentialFromArgs(
            String credentialLabel,
            String credentialLabelAlternative,
            String... args
    ) {
        String credential = null;
        int i = 0;
        while (i < args.length-1) {
            String parameter = args[i++];
            if (credentialLabel.equals(parameter) || credentialLabelAlternative.equals(parameter)) {
                credential = args[i++];
            }
        }

        return Optional.ofNullable(credential);
    }
}
