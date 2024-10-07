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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * <p>Class representing an OMERO Web Client.</p>
 * <p>
 *     It handles creating a connection with an OMERO server and keeping the connection alive.
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
 */
public class WebClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(WebClient.class);
    private static final long PING_DELAY_MILLISECONDS = 60000L;
    private final ObservableList<PixelAPI> availablePixelAPIs = FXCollections.observableArrayList();
    private final ObservableList<PixelAPI> availablePixelAPIsImmutable = FXCollections.unmodifiableObservableList(availablePixelAPIs);
    private final ObjectProperty<PixelAPI> selectedPixelAPI = new SimpleObjectProperty<>();
    private final ObservableSet<URI> openedImagesURIs = FXCollections.observableSet();
    private final ObservableSet<URI> openedImagesURIsImmutable = FXCollections.unmodifiableObservableSet(openedImagesURIs);
    private final Server server;
    private final ApisHandler apisHandler;
    private final String username;
    private final boolean authenticated;
    private final List<PixelAPI> allPixelAPIs;
    private final Timer timeoutTimer;
    private final String sessionUuid;
    private final Status status;
    private final FailReason failReason;

    /**
     * Status of a client creation
     */
    public enum Status {
        /**
         * The client creation was cancelled by the user
         */
        CANCELED,
        /**
         * The client creation failed
         */
        FAILED,
        /**
         * The client creation succeeded
         */
        SUCCESS
    }

    /**
     * Reason why a client creation failed
     */
    public enum FailReason {
        /**
         * A client with the same URI is already being created
         */
        ALREADY_CREATING,
        /**
         * The format of the provided URI is incorrect
         */
        INVALID_URI_FORMAT
    }

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
            List<PixelAPI> allPixelAPIs,
            Timer timeoutTimer,
            String sessionUuid,
            Status status,
            FailReason failReason
    ) {
        this.server = server;
        this.apisHandler = apisHandler;
        this.username = username;
        this.authenticated = authenticated;
        this.allPixelAPIs = allPixelAPIs;
        this.timeoutTimer = timeoutTimer;
        this.sessionUuid = sessionUuid;
        this.status = status;
        this.failReason = failReason;

        if (timeoutTimer != null) {
            timeoutTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    apisHandler.ping().thenAccept(success -> {
                        if (!success) {
                            logger.warn("Ping failed. Removing client");
                            WebClients.removeClient(WebClient.this);
                        }
                    });
                }
            }, PING_DELAY_MILLISECONDS, PING_DELAY_MILLISECONDS);
        }

        if (allPixelAPIs != null) {
            setUpPixelAPIs();
        }

        if (status.equals(Status.SUCCESS)) {
            String message = String.format("Connected to the OMERO.web instance at %s", apisHandler.getWebServerURI());
            if (authenticated) {
                message += String.format(" with user %s", username);
            } else {
                message += "with unauthenticated user";
            }
            logger.info(message);
        }
    }

    private WebClient(Status status, FailReason failReason) {
        this(null, null, null, false, null, null, null, status, failReason);
    }

    private WebClient(Status status) {
        this(status, null);
    }

    /**
     * <p>
     *     Static factory method creating a new client.
     *     It will initialize the connection and ask for credentials if this is required to access the server.
     * </p>
     * <p>
     *     This function should only be used by {@link WebClients WebClients}
     *     which monitors opened clients (see {@link WebClients#createClient(String, Authentication, String...)}).
     * </p>
     * <p>
     *     Note that this function is not guaranteed to create a valid client. Call the
     *     {@link #getStatus()} function to check the validity of the returned client
     *     before using it. If a client is not valid, some functions of this class
     *     will throw exceptions.
     * </p>
     * <p>The optional arguments must have one of the following format:</p>
     * <ul>
     *     <li>{@code --username [username] --password [password]}</li>
     *     <li>{@code -u [username] -p [password]}</li>
     * </ul>
     * <p>This function is asynchronous.</p>
     *
     * @param uri  the server URI to connect to
     * @param authentication  how to handle authentication
     * @param args  optional arguments to authenticate (see description above)
     * @return a CompletableFuture with the client
     */
    static CompletableFuture<WebClient> create(URI uri, Authentication authentication, String... args) {
        return ApisHandler.create(uri).thenApplyAsync(apisHandler -> {
            if (apisHandler.isPresent()) {
                return authenticateAndCreateClient(apisHandler.get(), uri, authentication, args);
            } else {
                return new WebClient(Status.FAILED);
            }
        });
    }

    /**
     * <p>Synchronous version of {@link #create(URI, Authentication, String...)}.</p>
     * <p>This function may block the calling thread for around a second.</p>
     */
    static WebClient createSync(URI uri, Authentication authentication, String... args) {
        Optional<ApisHandler> apisHandler = Optional.empty();
        try {
            apisHandler = ApisHandler.create(uri).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error initializing client", e);
        }

        if (apisHandler.isPresent()) {
            return authenticateAndCreateClient(apisHandler.get(), uri, authentication, args);
        } else {
            return new WebClient(Status.FAILED);
        }
    }

    /**
     * <p>Creates an invalid client.</p>
     * <p>
     *     This function should only be used by {@link WebClients WebClients}
     *     which monitors opened clients.
     * </p>
     *
     * @param failReason  the reason why the creation failed
     * @return an invalid client
     */
    static WebClient createInvalidClient(FailReason failReason) {
        return new WebClient(Status.FAILED, failReason);
    }

    @Override
    public void close() throws Exception {
        if (status.equals(Status.SUCCESS)) {
            logger.info(String.format("Disconnected from the OMERO.web instance at %s", apisHandler.getWebServerURI()));
        }
        if (allPixelAPIs != null) {
            for (PixelAPI pixelAPI: allPixelAPIs) {
                pixelAPI.close();
            }
        }
        if (apisHandler != null) {
            apisHandler.close();
        }
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
        }
    }

    @Override
    public String toString() {
        return String.format("""
                Web client of:
                    status: %s
                    username: %s
                    authenticated: %s
                    selectedPixelAPI: %s
                """, status, username, authenticated, selectedPixelAPI.get());
    }

    /**
     * @return the status of the client
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return the reason why the client creation failed, or an empty Optional
     * if the reason was not specified
     */
    public Optional<FailReason> getFailReason() {
        return Optional.ofNullable(failReason);
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
     * @return the session UUID of the authenticated user, or an empty Optional if
     * there is no authentication
     */
    public Optional<String> getSessionUuid() {
        return Optional.ofNullable(sessionUuid);
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
     * @param pixelAPIClass  the class of the pixel API to retrieve
     * @return the pixel API corresponding to the class passed in parameter
     * @param <T>  the class of the pixel API to retrieve
     * @throws IllegalArgumentException if the pixel API was not found
     */
    public <T extends PixelAPI> T getPixelAPI(Class<T> pixelAPIClass) {
        var pixelAPI = allPixelAPIs.stream()
                .filter(pixelAPIClass::isInstance)
                .map(pixelAPIClass::cast)
                .findAny();

        if (pixelAPI.isPresent()) {
            return pixelAPI.get();
        } else {
            throw new IllegalArgumentException("The pixel API was not found");
        }
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
                if (n && !availablePixelAPIs.contains(pixelAPI)) {
                    availablePixelAPIs.add(pixelAPI);
                } else {
                    availablePixelAPIs.remove(pixelAPI);
                }
            });
        }

        selectedPixelAPI.set(availablePixelAPIs.stream()
                .filter(PixelAPI::canAccessRawPixels)
                .findAny()
                .orElse(availablePixelAPIs.get(0))
        );
        availablePixelAPIs.addListener((ListChangeListener<? super PixelAPI>) change ->
                selectedPixelAPI.set(availablePixelAPIs.stream()
                        .filter(PixelAPI::canAccessRawPixels)
                        .findAny()
                        .orElse(availablePixelAPIs.get(0))
                ));
    }

    private static WebClient authenticateAndCreateClient(ApisHandler apisHandler, URI uri, Authentication authentication, String... args) {
        LoginResponse loginResponse = authenticate(apisHandler, uri, authentication, args);

        return switch (loginResponse.getStatus()) {
            case CANCELED -> new WebClient(Status.CANCELED);
            case FAILED -> new WebClient(Status.FAILED);
            case UNAUTHENTICATED, SUCCESS -> {
                try {
                    Optional<Server> server = loginResponse.getStatus().equals(LoginResponse.Status.SUCCESS) ?
                            Server.create(apisHandler, loginResponse.getGroup(), loginResponse.getUserId()).get() :
                            Server.create(apisHandler).get();

                    yield server.map(s -> new WebClient(
                            s,
                            apisHandler,
                            s.getConnectedOwner().username(),
                            loginResponse.getStatus().equals(LoginResponse.Status.SUCCESS),
                            List.of(
                                    new WebAPI(apisHandler),
                                    new IceAPI(apisHandler, loginResponse.getStatus().equals(LoginResponse.Status.SUCCESS), loginResponse.getSessionUuid()),
                                    new MsPixelBufferAPI(apisHandler)
                            ),
                            loginResponse.getStatus().equals(LoginResponse.Status.SUCCESS) ? new Timer("omero-keep-alive", true) : null,
                            loginResponse.getSessionUuid(),
                            Status.SUCCESS,
                            null
                    )).orElseGet(() -> new WebClient(Status.FAILED));
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Error initializing client", e);
                    yield new WebClient(Status.FAILED);
                }
            }
        };
    }

    private static LoginResponse authenticate(ApisHandler apisHandler, URI uri, Authentication authentication, String... args) {
        try {
            Optional<String> usernameFromArgs = getCredentialFromArgs("--username", "-u", args);
            Optional<String> passwordFromArgs = getCredentialFromArgs("--password", "-p", args);

            return switch (authentication) {
                case ENFORCE -> apisHandler.login(
                        usernameFromArgs.orElse(null),
                        passwordFromArgs.orElse(null)
                ).get();
                case TRY_TO_SKIP -> {
                    if (apisHandler.canSkipAuthentication()) {
                        yield LoginResponse.createNonSuccessfulLoginResponse(LoginResponse.Status.UNAUTHENTICATED);
                    } else {
                        yield apisHandler.login(
                                usernameFromArgs.orElse(null),
                                passwordFromArgs.orElse(null)
                        ).get();
                    }
                }
                case SKIP -> {
                    if (apisHandler.canSkipAuthentication()) {
                        yield LoginResponse.createNonSuccessfulLoginResponse(LoginResponse.Status.UNAUTHENTICATED);
                    } else {
                        logger.warn(String.format("The server %s doesn't allow browsing without being authenticated", uri));
                        yield LoginResponse.createNonSuccessfulLoginResponse(LoginResponse.Status.FAILED);
                    }
                }
            };
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error initializing client", e);
            return LoginResponse.createNonSuccessfulLoginResponse(LoginResponse.Status.FAILED);
        }
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
