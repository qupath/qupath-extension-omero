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
import qupath.ext.omero.core.entities.repositoryentities.Server;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.ext.omero.core.pixelapis.PixelAPI;
import qupath.ext.omero.core.pixelapis.ice.IceAPI;
import qupath.ext.omero.core.pixelapis.mspixelbuffer.MsPixelBufferAPI;
import qupath.ext.omero.core.pixelapis.web.WebAPI;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Client implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private static final int PING_DELAY_SECONDS = 60;
    private static final ObservableList<Client> clients = FXCollections.observableArrayList();
    private static final ObservableList<Client> clientsImmutable = FXCollections.unmodifiableObservableList(clients);
    private final ObservableList<PixelAPI> availablePixelAPIs = FXCollections.observableArrayList();
    private final ObservableList<PixelAPI> availablePixelAPIsImmutable = FXCollections.unmodifiableObservableList(availablePixelAPIs);
    private final ObjectProperty<PixelAPI> selectedPixelAPI = new SimpleObjectProperty<>();
    private final ObservableSet<URI> openedImagesURIs = FXCollections.observableSet();
    private final ObservableSet<URI> openedImagesURIsImmutable = FXCollections.unmodifiableObservableSet(openedImagesURIs);
    private final ScheduledExecutorService pingScheduler = Executors.newScheduledThreadPool(1);
    private final URI webServerUri;
    private final Credentials credentials;
    private final ApisHandler apisHandler;
    private final Server server;
    private final List<PixelAPI> allPixelAPIs;

    private Client(URI webServerUri, Credentials credentials) throws ExecutionException, InterruptedException {
        this.webServerUri = webServerUri;
        this.credentials = credentials;
        this.apisHandler = ApisHandler.create(webServerUri).get();
        this.server = switch (credentials.getUserType()) {
            case PUBLIC_USER -> Server.create(apisHandler).get();
            case REGULAR_USER -> {
                //TODO: this should be handled in ApisHandler.create?
                LoginResponse loginResponse = apisHandler.login(credentials.getUsername(), credentials.getPassword()).get();

                yield Server.create(apisHandler, loginResponse.getGroup(), loginResponse.getUserId()).get();
            }
        };
        this.allPixelAPIs = List.of(
                new WebAPI(apisHandler),
                switch (credentials.getUserType()) {
                    case PUBLIC_USER -> new IceAPI(apisHandler, false, null);
                    case REGULAR_USER -> {
                        //TODO: this should be handled in ApisHandler.create?
                        LoginResponse loginResponse = apisHandler.login(credentials.getUsername(), credentials.getPassword()).get();

                        yield new IceAPI(apisHandler, loginResponse.getStatus().equals(LoginResponse.Status.AUTHENTICATED), loginResponse.getSessionUuid());
                    }
                },
                new MsPixelBufferAPI(apisHandler)
        );

        if (credentials.getUserType().equals(Credentials.UserType.REGULAR_USER)) {
            pingScheduler.scheduleAtFixedRate(
                    () -> apisHandler.ping().exceptionally(error -> {
                        logger.error("Ping failed. Removing {}", Client.this.webServerUri, error);

                        try {
                            Client.this.close();
                        } catch (Exception e) {
                            logger.error("Error while closing {}", Client.this.webServerUri, e);
                        }
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
                credentials
        ));
    }

    /**
     * If no connection with url exists, create one
     * If connection with urls exists and credentials match, return matched
     * If connection with urls exists and credentials don't match, close existing and create one
     *
     * @param url
     * @param credentials
     * @return
     * @throws URISyntaxException
     */
    //TODO: runnable called when client closed?
    public static Client createOrGet(String url, Credentials credentials) throws Exception {
        URI webServerURI = WebUtilities.getServerURI(new URI(url)); //TODO: accept uri without scheme and automatically add https?

        synchronized (Client.class) {
            Optional<Client> existingClientWithUrl = clients.stream().filter(client -> client.webServerUri.getAuthority().equals(webServerURI.getAuthority())).findAny();
            if (existingClientWithUrl.isPresent()) {
                if (existingClientWithUrl.get().credentials.equals(credentials)) {
                    logger.debug("Found existing client of {} with corresponding user {}. Returning it", url, credentials);

                    return existingClientWithUrl.get();
                } else {
                    logger.debug(
                            "Found existing client of {} with different user {} than provided ({}). Closing it and creating new client",
                            url,
                            existingClientWithUrl.get().credentials,
                            credentials
                    );

                    existingClientWithUrl.get().close();
                }
            } else {
                logger.debug("No client of {} found. Creating new one", webServerURI);
            }

            Client client = new Client(webServerURI, credentials);
            clients.add(client);
            return client;
        }
    }

    @Override
    public void close() throws Exception {
        synchronized (Client.class) {
            clients.remove(this);
        }

        synchronized (this) {
            for (PixelAPI pixelAPI: allPixelAPIs) {
                pixelAPI.close();
            }
        }
        pingScheduler.close();
        apisHandler.close();

        logger.info(String.format("Disconnected from the OMERO.web instance at %s", apisHandler.getWebServerURI()));
    }

    @Override
    public String toString() {
        return String.format("Client of %s with %s", webServerUri, credentials);
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
        return Objects.equals(webServerUri, client.webServerUri) && Objects.equals(credentials, client.credentials);
    }

    @Override
    public int hashCode() {
        return Objects.hash(webServerUri, credentials);
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

    public Credentials getCredentials() {
        return credentials;
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
     * @param pixelAPI the pixel API to select
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
}
