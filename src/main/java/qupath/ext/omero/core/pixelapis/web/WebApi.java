package qupath.ext.omero.core.pixelapis.web;

import javafx.beans.property.FloatProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyFloatProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleFloatProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.ext.omero.core.pixelapis.PixelApiReader;
import qupath.lib.images.servers.PixelType;

import java.util.Map;

/**
 * This API uses the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html">OMERO JSON API</a>
 * to access pixel values of an image. It doesn't have dependencies but can only work with 8-bit RGB images, and the
 * images are JPEG-compressed.
 */
public class WebApi implements PixelApi {

    private static final String NAME = "Web";
    private static final float DEFAULT_JPEG_QUALITY = 0.9F;
    private static final String JPEG_QUALITY_PARAMETER = "--jpegQuality";
    private static final Logger logger = LoggerFactory.getLogger(WebApi.class);
    private final ApisHandler apisHandler;
    private final FloatProperty jpegQuality;

    /**
     * Creates a new WebApi.
     *
     * @param apisHandler the api handler owning this API
     */
    public WebApi(ApisHandler apisHandler) {
        this.apisHandler = apisHandler;
        this.jpegQuality = new SimpleFloatProperty(
                PreferencesManager.getWebJpegQuality(apisHandler.getWebServerURI()).orElse(DEFAULT_JPEG_QUALITY)
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof WebApi webAPI))
            return false;
        return webAPI.apisHandler.equals(apisHandler);
    }

    @Override
    public int hashCode() {
        return apisHandler.hashCode();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getArgs() {
        return Map.of(JPEG_QUALITY_PARAMETER, String.valueOf(jpegQuality.get()));
    }

    @Override
    public ReadOnlyBooleanProperty isAvailable() {
        return new SimpleBooleanProperty(true);
    }

    @Override
    public boolean canAccessRawPixels() {
        return false;
    }

    @Override
    public boolean canReadImage(PixelType pixelType) {
        return pixelType.equals(PixelType.UINT8);
    }

    @Override
    public boolean canReadImage(int numberOfChannels) {
        return numberOfChannels == 3;
    }

    /**
     * Creates a {@link WebReader} that will be used to read pixel values of an image.
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
     *             creation: {@link #JPEG_QUALITY_PARAMETER} to a float between 0 and 1 to change
     *             the JPEG quality of the returned images
     * @return a new web reader corresponding to this API
     * @throws IllegalArgumentException when the provided image cannot be read by this API
     * (see {@link #canReadImage(PixelType, int)})
     */
    @Override
    public PixelApiReader createReader(long id, ImageServerMetadata metadata, Map<String, String> args) {
        if (!canReadImage(metadata.getPixelType(), metadata.getSizeC())) {
            throw new IllegalArgumentException("The provided image cannot be read by this API");
        }

        if (args.containsKey(JPEG_QUALITY_PARAMETER)) {
            String quality = args.get(JPEG_QUALITY_PARAMETER);
            try {
                setJpegQuality(Float.parseFloat(quality));
            } catch (NumberFormatException e) {
                logger.warn("Can't convert {} to float", quality, e);
            }
        }

        return new WebReader(
                apisHandler,
                id,
                metadata.getPreferredTileWidth(),
                metadata.getPreferredTileHeight(),
                jpegQuality.get()
        );
    }

    @Override
    public String toString() {
        return String.format("Web API of %s", apisHandler.getWebServerURI());
    }

    @Override
    public void close() {}

    /**
     * @return the JPEG quality used by this pixel API.
     * This property may be updated from any thread
     */
    public ReadOnlyFloatProperty getJpegQuality() {
        return jpegQuality;
    }

    /**
     * Set the JPEG quality used by this pixel API.
     *
     * @param jpegQuality the JPEG quality (number between 0 and 1)
     */
    public void setJpegQuality(float jpegQuality) {
        if (jpegQuality > 0 && jpegQuality <= 1) {
            this.jpegQuality.set(jpegQuality);

            PreferencesManager.setWebJpegQuality(
                    apisHandler.getWebServerURI(),
                    jpegQuality
            );

            logger.debug("JPEG quality of web API changed to {}", jpegQuality);
        } else {
            logger.warn("Requested JPEG quality '{}' is invalid, must be between 0 and 1.", jpegQuality);
        }
    }
}
