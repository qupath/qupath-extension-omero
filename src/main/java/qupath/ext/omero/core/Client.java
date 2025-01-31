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
import qupath.ext.omero.core.entities.repositoryentities.Server;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.core.pixelapis.ice.IceApi;
import qupath.ext.omero.core.pixelapis.mspixelbuffer.MsPixelBufferApi;
import qupath.ext.omero.core.pixelapis.web.WebApi;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A class representing a connection to an OMERO web server.
 * <p>
 * A client can be connected to a server without being authenticated if the server allows it.
 * <p>
 * One client corresponds to one user, which means a new client must be created to switch user.
 * However, it is not allowed to have two different connections to the same server at the same time.
 * <p>
 * It has a reference to a {@link ApisHandler} which can be used to retrieve information from the OMERO server,
 * and a reference to a {@link Server Server} which is the ancestor of all OMERO entities.
 * <p>
 * A client must be {@link #close() closed} once no longer used.
 * <p>
 * This class is thread-safe.
 */
public class Client implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private static final int PING_DELAY_SECONDS = 60;
    private static final ObservableList<Client> clients = FXCollections.observableList(new CopyOnWriteArrayList<>());
    private static final ObservableList<Client> clientsImmutable = FXCollections.unmodifiableObservableList(clients);
    private final ObservableList<PixelApi> availablePixelApis = FXCollections.observableList(new CopyOnWriteArrayList<>());
    private final ObservableList<PixelApi> availablePixelAPIsImmutable = FXCollections.unmodifiableObservableList(availablePixelApis);
    private final ObjectProperty<PixelApi> selectedPixelAPI = new SimpleObjectProperty<>();
    private final ObservableSet<URI> openedImagesURIs = FXCollections.observableSet(new CopyOnWriteArraySet<>());
    private final ObservableSet<URI> openedImagesURIsImmutable = FXCollections.unmodifiableObservableSet(openedImagesURIs);
    private final ApisHandler apisHandler;
    private final List<PixelApi> allPixelApis;
    private ScheduledExecutorService pingScheduler;
    private CompletableFuture<Server> server;

    private Client(URI webServerUri, Credentials credentials) throws ExecutionException, InterruptedException, URISyntaxException {
        this.apisHandler = new ApisHandler(webServerUri, credentials);
        this.allPixelApis = List.of(
                new WebApi(apisHandler),
                new IceApi(apisHandler),
                new MsPixelBufferApi(apisHandler)
        );

        if (credentials.userType().equals(Credentials.UserType.REGULAR_USER)) {
            pingScheduler = Executors.newScheduledThreadPool(1);
            pingScheduler.scheduleAtFixedRate(
                    () -> apisHandler.ping().exceptionally(error -> {
                        logger.error("Ping failed. Closing connection to {}", Client.this.apisHandler.getWebServerURI(), error);

                        try {
                            Client.this.close();
                        } catch (Exception e) {
                            logger.error("Error while closing {}", Client.this.apisHandler.getWebServerURI(), e);
                        }
                        return null;
                    }),
                    0,
                    PING_DELAY_SECONDS,
                    TimeUnit.SECONDS
            );
        }

        setUpPixelAPIs();

        clients.add(this);

        PreferencesManager.addServer(webServerUri, credentials);

        logger.info("Connected to the OMERO.web instance at {} with {}", apisHandler.getWebServerURI(), credentials);
    }

    /**
     * Create or get a connection to the provided server:
     * <ul>
     *     <li>If no connection with the provided server exists, a new one is created.</li>
     *     <li>
     *         If a connection with the provided server exists and the connected user match the provided one in the credentials,
     *         the existing connection is returned.</li>
     *     <li>
     *         If a connection with the provided server exists and the connected user doesn't match the provided one in the credentials,
     *         the existing connection is closed and a new one is created.
     *     </li>
     * </ul>
     * This will send a few requests to get basic information on the server and authenticate if necessary,
     * so it can take a few seconds. However, this operation is cancellable.
     *
     * @param url the URL to the OMERO web server to connect to
     * @param credentials the credentials to use for the authentication
     * @return a connection to the provided server
     * @throws URISyntaxException if a link to the server cannot be created
     * @throws ExecutionException if a request to the server fails or if a response does not contain expected elements.
     * This can happen if the server is unreachable or if the authentication fails for example
     * @throws InterruptedException if the running thread is interrupted
     * @throws IllegalArgumentException if the server doesn't return all necessary information on it, or if the root account
     * was used to log in
     */
    //TODO: runnable called when client closed?
    public static Client createOrGet(String url, Credentials credentials) throws URISyntaxException, ExecutionException, InterruptedException {
        URI webServerURI = Utils.getServerURI(new URI(url));

        synchronized (Client.class) {
            Optional<Client> existingClientWithUrl = clients.stream()
                    .filter(client -> client.apisHandler.getWebServerURI().getAuthority().equals(webServerURI.getAuthority()))
                    .findAny();
            if (existingClientWithUrl.isPresent()) {
                if (existingClientWithUrl.get().apisHandler.getCredentials().equals(credentials)) {
                    logger.debug("Found existing client of {} with corresponding user {}. Returning it", url, credentials);

                    return existingClientWithUrl.get();
                } else {
                    logger.debug(
                            "Found existing client of {} with different user {} than provided ({}). Closing it and creating new client",
                            url,
                            existingClientWithUrl.get().apisHandler.getCredentials(),
                            credentials
                    );

                    try {
                        existingClientWithUrl.get().close();
                    } catch (Exception e) {
                        if (e instanceof InterruptedException interruptedException) {
                            throw interruptedException;
                        }
                        logger.warn("Cannot close connection with {}. This might ", existingClientWithUrl.get(), e);
                    }
                }
            } else {
                logger.debug("No client of {} found. Creating new one", webServerURI);
            }

            return new Client(webServerURI, credentials);
        }
    }

    @Override
    public void close() throws Exception {
        //TODO: this is sometimes called in UI but can take some time
        synchronized (Client.class) {
            clients.remove(this);
        }

        synchronized (this) {
            for (PixelApi pixelAPI: allPixelApis) {
                pixelAPI.close();
            }
        }
        if (pingScheduler != null) {
            pingScheduler.close();
        }
        apisHandler.close();

        logger.info("Disconnected from the OMERO.web instance at {}", apisHandler.getWebServerURI());
    }

    @Override
    public String toString() {
        return String.format("Client of %s with %s", apisHandler.getWebServerURI(), apisHandler.getCredentials());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        Client client = (Client) object;
        return Objects.equals(apisHandler, client.apisHandler);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apisHandler);
    }

    /**
     * Returns an unmodifiable list of all connected (but not necessarily authenticated) clients.
     * <p>
     * This list may be updated from any thread.
     *
     * @return the connected clients
     */
    public static ObservableList<Client> getClients() {
        return clientsImmutable;
    }

    /**
     * Retrieve the client corresponding to the provided uri.
     *
     * @param uri the web server URI of the client to retrieve
     * @return the client corresponding to the URI, or an empty Optional if not found
     */
    public static synchronized Optional<Client> getClientFromURI(URI uri) {
        return clients.stream().filter(client -> client.getApisHandler().getWebServerURI().equals(uri)).findAny();
    }

    /**
     * @return the {@link ApisHandler} of this client
     */
    public ApisHandler getApisHandler() {
        return apisHandler;
    }

    /**
     * Get the {@link Server Server} of this client. It is initialized upon the first call to
     * this function.
     * Note that the returned CompletableFuture may complete exceptionally.
     *
     * @return a CompletableFuture (that may complete exceptionally) with the {@link Server Server} of this client
     */
    public synchronized CompletableFuture<Server> getServer() {
        if (server == null) {
            server = CompletableFuture.supplyAsync(() -> {
                try {
                    return new Server(apisHandler);
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return server;
    }

    /**
     * @return the currently selected pixel API. This property may be updated from any thread
     * and may be null
     */
    public ReadOnlyObjectProperty<PixelApi> getSelectedPixelAPI() {
        return selectedPixelAPI;
    }

    /**
     * Set the currently selected pixel API of this client.
     *
     * @param pixelAPI the pixel API to select
     * @throws IllegalArgumentException when the provided pixel API is not available
     * or not part of the pixel APIs of this client
     */
    public void setSelectedPixelAPI(PixelApi pixelAPI) {
        if (!pixelAPI.isAvailable().get()) {
            throw new IllegalArgumentException("The provided pixel API is not available");
        }
        if (!allPixelApis.contains(pixelAPI)) {
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
    public ObservableList<PixelApi> getAvailablePixelAPIs() {
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
    public synchronized <T extends PixelApi> T getPixelAPI(Class<T> pixelAPIClass) {
        return allPixelApis.stream()
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
     * @param imageURI the image URI
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
        availablePixelApis.setAll(allPixelApis.stream()
                .filter(pixelAPI -> pixelAPI.isAvailable().get())
                .toList()
        );
        for (PixelApi pixelAPI: allPixelApis) {
            pixelAPI.isAvailable().addListener((p, o, n) -> {
                synchronized (this) {
                    if (n && !availablePixelApis.contains(pixelAPI)) {
                        availablePixelApis.add(pixelAPI);
                    } else {
                        availablePixelApis.remove(pixelAPI);
                    }
                }
            });
        }

        selectedPixelAPI.set(availablePixelApis.stream()
                .filter(PixelApi::canAccessRawPixels)
                .findAny()
                .orElse(availablePixelApis.getFirst())
        );
        availablePixelApis.addListener((ListChangeListener<? super PixelApi>) change -> {
            synchronized (this) {
                selectedPixelAPI.set(availablePixelApis.stream()
                        .filter(PixelApi::canAccessRawPixels)
                        .findAny()
                        .orElse(availablePixelApis.getFirst()));
            }
        });
    }
}
