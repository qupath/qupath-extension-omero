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
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.core.pixelapis.PixelApiReader;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This API uses the <a href="https://omero.readthedocs.io/en/v5.6.7/developers/Java.html">OMERO gateway</a>
 * to access pixel values of an image. Any image can be used, and pixel values are accurate.
 * <p>
 * This pixel API must be {@link #close() closed} once no longer used.
 */
public class IceApi implements PixelApi {

    private static final String NAME = "Ice";
    private static final String ADDRESS_PARAMETER = "--serverAddress";
    private static final String PORT_PARAMETER = "--serverPort";
    private static final Logger logger = LoggerFactory.getLogger(IceApi.class);
    private static final boolean gatewayAvailable;
    private final Map<Long, IceReader> readers = new HashMap<>();
    private final ApisHandler apisHandler;
    private final StringProperty serverAddress;
    private final IntegerProperty serverPort;
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
        this.apisHandler = apisHandler;
        this.serverAddress = new SimpleStringProperty(
                PreferencesManager.getIceAddress(apisHandler.getWebServerURI()).orElse("")
        );
        this.serverPort = new SimpleIntegerProperty(
                PreferencesManager.getIcePort(apisHandler.getWebServerURI()).orElse(0)
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
                PORT_PARAMETER, String.valueOf(serverPort.get())
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
     * <p>
     * Note that you shouldn't {@link PixelApiReader#close() close} this reader when it's
     * no longer used. This pixel API will close them when it itself is closed.
     * <p>
     * Note that if this API is not available (see {@link #isAvailable()}), calling this function
     * will result in undefined behavior.
     *
     * @param id the ID of the image to open
     * @param metadata the metadata of the image to open
     * @param args additional arguments containing label to parameter values to change the reader
     *             creation: {@link #ADDRESS_PARAMETER} to a string to set the address used to
     *             communicate with the OMERO server and {@link #PORT_PARAMETER} to an integer
     *             greater than 0 to change the port this microservice uses on the OMERO server
     * @return a new web reader corresponding to this API
     * @throws IllegalArgumentException when the provided image cannot be read by this API
     * (see {@link #canReadImage(PixelType, int)})
     */
    @Override
    public PixelApiReader createReader(long id, ImageServerMetadata metadata, Map<String, String> args) throws IOException {
        if (!canReadImage(metadata.getPixelType(), metadata.getSizeC())) {
            throw new IllegalArgumentException("The provided image cannot be read by this API");
        }

        if (args.containsKey(ADDRESS_PARAMETER)) {
            setServerAddress(args.get(ADDRESS_PARAMETER));
        }
        if (args.containsKey(PORT_PARAMETER)) {
            String port = args.get(PORT_PARAMETER);
            try {
                setServerPort(Integer.parseInt(port));
            } catch (NumberFormatException e) {
                logger.warn("Can't convert {} to integer", port, e);
            }
        }

        synchronized (this) {
            if (gatewayWrapper == null) {
                logger.debug("Gateway null. Creating one...");

                try {
                    String sessionUuid = apisHandler.getSessionUuid();

                    List<LoginCredentials> credentials = new ArrayList<>();
                    if (serverAddress.get() != null && !serverAddress.get().isEmpty()) {
                        credentials.add(new LoginCredentials(sessionUuid, sessionUuid, serverAddress.get(), serverPort.get()));
                    }
                    credentials.add(new LoginCredentials(sessionUuid, sessionUuid, apisHandler.getWebServerURI().getHost(), apisHandler.getServerPort()));
                    credentials.add(new LoginCredentials(sessionUuid, sessionUuid, apisHandler.getServerURI(), apisHandler.getServerPort()));

                    gatewayWrapper = new GatewayWrapper(credentials);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }

            try {
                return readers.computeIfAbsent(id, i -> {
                    logger.debug("No reader for image with ID {} found. Creating one...", i);

                    try {
                        return new IceReader(gatewayWrapper, id, metadata.getChannels());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                throw new IOException(e);
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
        if (gatewayWrapper != null) {
            gatewayWrapper.close();
        }

        for (IceReader reader: readers.values()) {
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
     * @param serverPort the port of the OMERO server
     */
    public void setServerPort(int serverPort) {
        this.serverPort.set(serverPort);

        PreferencesManager.setIcePort(
                apisHandler.getWebServerURI(),
                serverPort
        );

        logger.debug("ICE server port set to {}", serverPort);
    }
}
