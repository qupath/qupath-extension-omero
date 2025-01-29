package qupath.ext.omero.core.pixelapis;

import qupath.lib.images.servers.TileRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * <p>
 *     This interface allows to read pixel values from a tile request.
 *     It should only be created by a {@link PixelApi}.
 * </p>
 * <p>
 *     Once no longer used, any instance of this interface must be {@link #close() closed}.
 * </p>
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
}
