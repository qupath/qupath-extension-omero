package qupath.ext.omero.core.pixelapis;

import javafx.beans.value.ObservableBooleanValue;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;

import java.io.IOException;
import java.util.Map;

/**
 * This interface provides information (e.g. types of image supported) on a specific API to access
 * pixel values of an OMERO image. It can also be used to create a {@link PixelApiReader}
 * corresponding to this API.
 * <p>
 * A PixelApi must be {@link #close() closed} once no longer used.
 */
public interface PixelApi extends AutoCloseable {

    /**
     * @return a human-readable name of this API
     */
    String getName();

    /**
     * @return arguments currently used internally by this pixel API
     */
    Map<String, String> getArgs();

    /**
     * @return whether this API can be used. This property may be updated from any thread
     */
    ObservableBooleanValue isAvailable();

    /**
     * @return whether pixel values returned by this API are accurate (and not JPEG-compressed for example)
     */
    boolean canAccessRawPixels();

    /**
     * Indicates if an image with the provided parameters can be read by this API.
     * This method shouldn't need to be overridden.
     *
     * @param pixelType the pixel type of the image
     * @param numberOfChannels the number of channels of the image
     * @return whether the image can be read
     */
    default boolean canReadImage(PixelType pixelType, int numberOfChannels) {
        return canReadImage(pixelType) && canReadImage(numberOfChannels);
    }

    /**
     * Indicates if an image with the provided parameters can be read by this API.
     *
     * @param pixelType the pixel type of the image
     * @return whether the image can be read
     */
    boolean canReadImage(PixelType pixelType);

    /**
     * Indicates if an image with the provided parameters can be read by this API.
     *
     * @param numberOfChannels the number of channels of the image
     * @return whether the image can be read
     */
    boolean canReadImage(int numberOfChannels);

    /**
     * Creates a {@link PixelApiReader} corresponding to this API that will be used to read
     * pixel values of an image.
     * <p>
     * Note that you shouldn't {@link PixelApiReader#close() close} this reader when it's
     * no longer used. This pixel API will close them when it itself is closed.
     *
     * @param id the ID of the image to open
     * @param metadata the metadata of the image to open
     * @param args additional arguments containing label to parameter values to change the reader
     *             creation. See the description of the pixel API implementation for more details
     * @return a new reader corresponding to this API
     * @throws IOException when the reader creation fails
     * @throws IllegalStateException when this API is not available (see {@link #isAvailable()})
     * @throws IllegalArgumentException when the provided image cannot be read by this API
     * (see {@link #canReadImage(PixelType, int)})
     */
    PixelApiReader createReader(
            long id,
            ImageServerMetadata metadata,
            Map<String, String> args
    ) throws IOException;
}
