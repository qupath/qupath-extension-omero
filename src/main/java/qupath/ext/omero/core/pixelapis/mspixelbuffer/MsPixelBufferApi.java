package qupath.ext.omero.core.pixelapis.mspixelbuffer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableBooleanValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.ArgsUtils;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.core.pixelapis.PixelApiReader;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * This API uses the <a href="https://github.com/glencoesoftware/omero-ms-pixel-buffer">OMERO Pixel Data Microservice</a>
 * to access pixel values of an image. Any image can be used, and pixel values are accurate.
 * However, the server needs to have this microservice installed.
 */
public class MsPixelBufferApi implements PixelApi {

    private static final String NAME = "Pixel Buffer Microservice";
    private static final int DEFAULT_PORT = 8082;
    private static final String PORT_PARAMETER = "--msPixelBufferPort";
    private static final Logger logger = LoggerFactory.getLogger(MsPixelBufferApi.class);
    private final ApisHandler apisHandler;
    private final BooleanProperty isAvailable = new SimpleBooleanProperty(false);
    private final IntegerProperty port;
    private String host;

    /**
     * Creates a new MsPixelBufferApi. Note that {@link #isAvailable()} may take a few seconds to
     * be accurate (it will be false by default and true if a request succeeds, which may take some
     * time).
     *
     * @param apisHandler the apis handler owning this API
     */
    public MsPixelBufferApi(ApisHandler apisHandler) {
        this.apisHandler = apisHandler;
        this.port = new SimpleIntegerProperty(
                PreferencesManager.getMsPixelBufferPort(apisHandler.getWebServerURI()).orElse(DEFAULT_PORT)
        );

        setHost();
        setAvailable(true);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getArgs() {
        return Map.of(PORT_PARAMETER, String.valueOf(port.get()));
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
     * Creates a {@link MsPixelBufferReader} that will be used to read pixel values of an image.
     * <p>
     * Note that you shouldn't {@link PixelApiReader#close() close} this reader when it's
     * no longer used. This pixel API will close them when it itself is closed.
     * <p>
     * Note that if this API is not available (see {@link #isAvailable()}), calling this function
     * will result in undefined behavior.
     *
     * @param id the ID of the image to open
     * @param metadata the metadata of the image to open
     * @param args additional arguments to change the reader creation: {@link #PORT_PARAMETER} to
     *             an integer greater than 0 to change the port this microservice uses on the OMERO server
     * @return a new mx pixel buffer reader corresponding to this API
     * @throws IllegalStateException when this API is not available (see {@link #isAvailable()})
     * @throws IllegalArgumentException when the provided image cannot be read by this API
     * (see {@link #canReadImage(PixelType, int)})
     */
    @Override
    public PixelApiReader createReader(long id, ImageServerMetadata metadata, List<String> args) {
        if (!canReadImage(metadata.getPixelType(), metadata.getSizeC())) {
            throw new IllegalArgumentException("The provided image cannot be read by this API");
        }

        ArgsUtils.findArgInList(PORT_PARAMETER, args)
                .ifPresent(port -> {
                    try {
                        setPort(Integer.parseInt(port), true);
                    } catch (NumberFormatException e) {
                        logger.warn("Can't convert {} to integer", port, e);
                    }
                });

        return new MsPixelBufferReader(
                host,
                apisHandler,
                id,
                metadata.getPixelType(),
                metadata.getChannels(),
                metadata.nLevels()
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof MsPixelBufferApi msPixelBufferAPI))
            return false;
        return msPixelBufferAPI.apisHandler.equals(apisHandler);
    }

    @Override
    public int hashCode() {
        return apisHandler.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Ms pixel buffer API of %s", apisHandler.getWebServerURI());
    }

    @Override
    public void close() {}

    /**
     * @return the port used by this microservice on the OMERO server.
     * This property may be updated from any thread
     */
    public ReadOnlyIntegerProperty getPort() {
        return port;
    }

    /**
     * Set the port used by this microservice on the OMERO server.
     * This may change the availability of this pixel API.
     *
     * @param port the new port this microservice uses on the OMERO server
     * @param checkAvailabilityNow whether to directly check if the new port changes the
     *                             availability of this pixel API. If false, the check will
     *                             be performed in the background (recommended to avoid blocking
     *                             the calling thread)
     */
    public void setPort(int port, boolean checkAvailabilityNow) {
        this.port.set(port);
        PreferencesManager.setMsPixelBufferPort(
                apisHandler.getWebServerURI(),
                port
        );
        logger.debug("Ms pixel buffer server port changed to {}", port);

        setHost();
        setAvailable(!checkAvailabilityNow);
    }

    private void setHost() {
        Optional<URI> uri = changePortOfURI(apisHandler.getWebServerURI(), port.get());
        if (uri.isPresent()) {
            host = uri.get().toString();
        } else {
            host = apisHandler.getWebServerURI().toString();
        }

        logger.debug("Ms pixel buffer server host changed to {}", host);
    }

    private void setAvailable(boolean performInBackground) {
        String url = String.format("%s/tile", host);
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            logger.error("Cannot create URI from {}", url, e);
            synchronized (this) {
                isAvailable.set(false);
            }
            return;
        }

        CompletableFuture<Void> request = apisHandler.isLinkReachable(uri, RequestSender.RequestType.OPTIONS).handle((v, error) -> {
            if (error == null) {
                synchronized (this) {
                    isAvailable.set(true);
                }
            } else {
                logger.debug("Connexion to {} failed", uri, error);
                synchronized (this) {
                    isAvailable.set(false);
                }
            }

            logger.debug("Ms pixel buffer availability changed to {}", isAvailable.get());
            return null;
        });

        if (!performInBackground) {
            try {
                request.get();
            } catch (ExecutionException | InterruptedException e) {
                logger.debug("Connexion to {} failed", uri, e);
            }
        }
    }

    private static Optional<URI> changePortOfURI(URI uri, int port) {
        try {
            return Optional.of(new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    port,
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            ));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
