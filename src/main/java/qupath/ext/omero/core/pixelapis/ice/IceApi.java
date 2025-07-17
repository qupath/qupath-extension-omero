package qupath.ext.omero.core.pixelapis.ice;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import omero.gateway.LoginCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.ArgsUtils;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.core.pixelapis.PixelApiReader;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * This API uses the <a href="https://omero.readthedocs.io/en/v5.6.7/developers/Java.html">OMERO gateway</a>
 * to access pixel values of an image. Any image can be used, and pixel values are accurate.
 * <p>
 * This pixel API must be {@link #close() closed} once no longer used.
 */
public class IceApi implements PixelApi {

    private static final Logger logger = LoggerFactory.getLogger(IceApi.class);
    private static final String NAME = "Ice";
    private static final String ADDRESS_PARAMETER = "--serverAddress";
    private static final String PORT_PARAMETER = "--serverPort";
    private static final String NUMBER_OF_READERS_PARAMETER = "--numberOfReaders";
    private static final int MIN_NUMBER_OF_READERS = 1;
    private static final int MAX_NUMBER_OF_READERS = Runtime.getRuntime().availableProcessors() + 5;    // Tasks that include I/O or other blocking operations
                                                                                                        // should use a bit more than the number of cores
    private static final int DEFAULT_NUMBER_OF_READERS = Math.min(MAX_NUMBER_OF_READERS, 10);           // The "10" parameter comes from
                                                                                                        // https://omero.readthedocs.io/en/stable/sysadmins/config.html#omero.threads.min_threads
    private static final boolean gatewayAvailable;
    private final List<IceReader> readers = new ArrayList<>();
    private final ApisHandler apisHandler;
    private final StringProperty serverAddress;
    private final IntegerProperty serverPort;
    private final IntegerProperty numberOfReaders;
    private final BooleanProperty isAvailable;
    private GatewayWrapper gatewayWrapper;

    static {
        boolean available = false;

        try {
            Class.forName("omero.gateway.Gateway");
            available = true;
            logger.debug("OMERO Ice gateway available ('omero.gateway.Gateway' found).");
        } catch (ClassNotFoundException e) {
            logger.debug("OMERO Ice gateway unavailable ('omero.gateway.Gateway' not found).");
        } finally {
            gatewayAvailable = available;
        }
    }

    /**
     * Create a new ICE API.
     *
     * @param apisHandler the apis handler owning this API
     */
    public IceApi(ApisHandler apisHandler) {
        logger.debug("Creating ICE API with {}", apisHandler);

        this.apisHandler = apisHandler;
        this.serverAddress = new SimpleStringProperty(
                PreferencesManager.getIceAddress(apisHandler.getWebServerURI()).orElse("")
        );
        this.serverPort = new SimpleIntegerProperty(
                PreferencesManager.getIcePort(apisHandler.getWebServerURI()).orElse(0)
        );
        this.numberOfReaders = new SimpleIntegerProperty(
                PreferencesManager.getIceNumberOfReaders(apisHandler.getWebServerURI()).orElse(DEFAULT_NUMBER_OF_READERS)
        );
        this.isAvailable = new SimpleBooleanProperty(apisHandler.getCredentials().userType().equals(Credentials.UserType.REGULAR_USER) && gatewayAvailable);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getArgs() {
        return Map.of(
                ADDRESS_PARAMETER, serverAddress.get(),
                PORT_PARAMETER, String.valueOf(serverPort.get()),
                NUMBER_OF_READERS_PARAMETER, String.valueOf(numberOfReaders.get())
        );
    }

    @Override
    public ObservableBooleanValue isAvailable() {
        return isAvailable;
    }

    @Override
    public boolean canAccessRawPixels() {
        return true;
    }

    @Override
    public boolean canReadImage(PixelType pixelType) {
        return !pixelType.equals(PixelType.INT8) && !pixelType.equals(PixelType.UINT32);
    }

    @Override
    public boolean canReadImage(int numberOfChannels) {
        return true;
    }

    /**
     * Creates an {@link IceReader} that will be used to read pixel values of an image.
     * This may take a few seconds.
     * <p>
     * Note that you should {@link PixelApiReader#close() close} the returned reader when it's
     * no longer used. As a precaution, this pixel API will (if needed) close them when it itself
     * is closed, but it's better if they are closed as soon as possible.
     * <p>
     * Note that if this API is not available (see {@link #isAvailable()}), calling this function
     * will result in undefined behavior.
     * <p>
     * Warning: if a reader of an image belonging to a different group than the group of the image to
     * open exists, then the reader will be closed. See {@link #closeReadersWithDifferentGroups(long, long)}
     * for more information.
     *
     * @param imageId the ID of the image to open
     * @param metadata the metadata of the image to open
     * @param args additional arguments to change the reader creation: {@link #ADDRESS_PARAMETER}
     *             to a string to set the address used to communicate with the OMERO server and
     *             {@link #PORT_PARAMETER} to an integer greater than 0 to change the port this
     *             microservice uses on the OMERO server
     * @return a new web reader corresponding to this API
     * @throws ExecutionException if an error occurred while creating the reader
     * @throws InterruptedException if the calling thread is interrupted while creating the reader
     * @throws IllegalArgumentException when the provided image cannot be read by this API
     * (see {@link #canReadImage(PixelType, int)})
     */
    @Override
    public PixelApiReader createReader(long imageId, ImageServerMetadata metadata, List<String> args) throws ExecutionException, InterruptedException {
        logger.debug("Getting or creating ICE reader to open image with ID {} with args {}", imageId, args);

        if (!canReadImage(metadata.getPixelType(), metadata.getSizeC())) {
            throw new IllegalArgumentException("The provided image cannot be read by this API");
        }

        ArgsUtils.findArgInList(ADDRESS_PARAMETER, args).ifPresent(this::setServerAddress);
        ArgsUtils.findArgInList(PORT_PARAMETER, args).ifPresent(port -> {
            try {
                setServerPort(Integer.parseInt(port));
            } catch (IllegalArgumentException e) {
                logger.warn("Can't use provided ICE server port {}", port, e);
            }
        });
        ArgsUtils.findArgInList(NUMBER_OF_READERS_PARAMETER, args).ifPresent(numberOfReaders -> {
            try {
                setNumberOfReaders(Integer.parseInt(numberOfReaders));
            } catch (IllegalArgumentException e) {
                logger.warn("Can't use provided ICE server number of readers {}", numberOfReaders, e);
            }
        });

        synchronized (this) {
            if (gatewayWrapper == null) {
                logger.debug("Gateway null. Creating one...");

                try {
                    String sessionUuid = apisHandler.getSessionUuid().orElseThrow();

                    List<LoginCredentials> credentials = new ArrayList<>();
                    if (serverAddress.get() != null && !serverAddress.get().isEmpty()) {
                        credentials.add(new LoginCredentials(sessionUuid, sessionUuid, serverAddress.get(), serverPort.get()));
                    }
                    credentials.add(new LoginCredentials(sessionUuid, sessionUuid, apisHandler.getWebServerURI().getHost(), apisHandler.getServerPort()));
                    credentials.add(new LoginCredentials(sessionUuid, sessionUuid, apisHandler.getServerURI(), apisHandler.getServerPort()));

                    gatewayWrapper = new GatewayWrapper(credentials);
                } catch (Exception e) {
                    throw new ExecutionException(e);
                }
            } else {
                logger.debug("Gateway not null, using it");
            }

            long groupId = apisHandler.getImage(imageId).get().getGroupId();

            closeReadersWithDifferentGroups(imageId, groupId);

            try {
                logger.debug("Creating reader for image with ID {}...", imageId);

                try {
                    IceReader reader = new IceReader(gatewayWrapper, imageId, groupId, metadata.getChannels(), numberOfReaders.get());
                    readers.add(reader);
                    return reader;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } catch (RuntimeException e) {
                throw new ExecutionException(e);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof IceApi iceApi))
            return false;
        return iceApi.apisHandler.equals(apisHandler);
    }

    @Override
    public int hashCode() {
        return apisHandler.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Ice API of %s", apisHandler.getWebServerURI());
    }

    @Override
    public synchronized void close() throws Exception {
        logger.debug("Closing ICE API of {}", apisHandler);

        if (gatewayWrapper != null) {
            gatewayWrapper.close();
        }

        for (IceReader reader: readers) {
            reader.close();
        }
    }

    /**
     * @return the address used to communicate with the OMERO server.
     * This property may be updated from any thread
     */
    public ReadOnlyStringProperty getServerAddress() {
        return serverAddress;
    }

    /**
     * Set the address used to communicate with the OMERO server.
     *
     * @param serverAddress the URL of the OMERO server
     */
    public void setServerAddress(String serverAddress) {
        this.serverAddress.set(serverAddress);

        PreferencesManager.setIceAddress(
                apisHandler.getWebServerURI(),
                serverAddress
        );

        logger.debug("ICE server address set to {}", serverAddress);
    }

    /**
     * @return the port used to communicate with the OMERO server.
     * This property may be updated from any thread
     */
    public ReadOnlyIntegerProperty getServerPort() {
        return serverPort;
    }

    /**
     * Set the port used to communicate with the OMERO server.
     *
     * @param serverPort the port of the OMERO server. It must be greater than 0
     * @throws IllegalArgumentException if the provided port is not greater than 0
     */
    public void setServerPort(int serverPort) {
        if (serverPort < 0) {
            throw new IllegalArgumentException(String.format("The provided server port %d is not greater than 0", serverPort));
        }

        this.serverPort.set(serverPort);
        PreferencesManager.setIcePort(
                apisHandler.getWebServerURI(),
                serverPort
        );

        logger.debug("ICE server port set to {}", serverPort);
    }

    /**
     * @return the (inclusive) minimum number of readers that can be used with this ICE API
     */
    public int getMinNumberOfReaders() {
        return MIN_NUMBER_OF_READERS;
    }

    /**
     * @return the (inclusive) maximum number of readers that can be used with this ICE API
     */
    public int getMaxNumberOfReaders() {
        return MAX_NUMBER_OF_READERS;
    }

    /**
     * @return the number of readers to use when reading an image with this API.
     * This property may be updated from any thread
     */
    public ReadOnlyIntegerProperty getNumberOfReaders() {
        return numberOfReaders;
    }

    /**
     * Set the number of readers to use when reading an image with this API.
     *
     * @param numberOfReaders the number of readers to use when reading an image with this API. It must be greater than or equal to {@link #getMinNumberOfReaders()}
     *                        and less than or equal to {@link #getMaxNumberOfReaders()}
     * @throws IllegalArgumentException if the provided number of readers does not respect the above specifications
     */
    public void setNumberOfReaders(int numberOfReaders) {
        if (numberOfReaders < getMinNumberOfReaders()) {
            throw new IllegalArgumentException(String.format("The provided number of readers %d is not greater than %d", numberOfReaders, getMinNumberOfReaders()));
        }

        this.numberOfReaders.set(numberOfReaders);
        PreferencesManager.setIceNumberOfReaders(
                apisHandler.getWebServerURI(),
                numberOfReaders
        );

        logger.debug("ICE number of readers set to {}", numberOfReaders);
    }

    /**
     * ICE doesn't allow reading several images of different groups at the same time (see
     * <a href="https://github.com/ome/omero-gateway-java/issues/98">this issue</a>).
     * Therefore, all readers of images belonging to different groups must be closed before
     * creating a new reader.
     *
     * @param imageId the ID of the image to open
     * @param groupId the ID of the group owning the image to open. If a reader with a different
     *                group than the provided one exists, it will be closed
     */
    private void closeReadersWithDifferentGroups(long imageId, long groupId) {
        List<IceReader> readersWithDifferentGroups = readers.stream()
                .filter(reader -> reader.getGroupId() != groupId)
                .toList();
        if (!readersWithDifferentGroups.isEmpty()) {
            logger.debug(
                    "Found readers {} with groups different from the group {} owning the image {} to open. Closing them...",
                    readersWithDifferentGroups,
                    groupId,
                    imageId
            );

            for (IceReader reader: readersWithDifferentGroups) {
                try {
                    reader.close();
                } catch (Exception e) {
                    logger.warn("Cannot close reader", e);
                }

                readers.remove(reader);
            }
        }
    }
}
