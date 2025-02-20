package qupath.ext.omero.core.pixelapis;

import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * This interface allows to read pixel values from a tile request.
 * It should only be created by a {@link PixelApi}.
 * <p>
 * Once no longer used, any instance of this interface must be {@link #close() closed}.
 */
public interface PixelApiReader extends AutoCloseable {

    /**
     * Read a tile of an image.
     *
     * @param tileRequest the tile parameters
     * @return the resulting image
     * @throws IOException when a reading error occurs
     */
    BufferedImage readTile(TileRequest tileRequest) throws IOException;

    /**
     * Let this pixel API modify some metadata. This may be needed when this pixel API uses
     * some metadata slightly different from the one returned with the web request. For example,
     * the tile size can be specific to one pixel API.
     *
     * @param originalMetadata the original metadata of the image
     * @return a modified (or not) version of the provided metadata
     */
    default ImageServerMetadata updateMetadata(ImageServerMetadata originalMetadata) {
        return originalMetadata;
    }
}
