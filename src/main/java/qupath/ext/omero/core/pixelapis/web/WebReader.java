package qupath.ext.omero.core.pixelapis.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.lib.images.servers.TileRequest;
import qupath.ext.omero.core.pixelapis.PixelApiReader;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Read pixel values using the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html">OMERO JSON API</a>.
 */
class WebReader implements PixelApiReader {

    private static final Logger logger = LoggerFactory.getLogger(WebReader.class);
    private final ApisHandler apisHandler;
    private final long imageID;
    private final int preferredTileWidth;
    private final int preferredTileHeight;
    private final double jpegQuality;

    /**
     * Creates a new WebApi.
     *
     * @param apisHandler  the request handler which will be used to perform web requests
     * @param imageID  the ID of the image to open
     * @param preferredTileWidth  the preferred tile width of the image to open in pixels
     * @param preferredTileHeight  the preferred tile height of the image to open in pixels
     * @param jpegQuality  the JPEG quality of the image to open (between 0 and 1)
     */
    public WebReader(
            ApisHandler apisHandler,
            long imageID,
            int preferredTileWidth,
            int preferredTileHeight,
            float jpegQuality
    ) {
        this.apisHandler = apisHandler;
        this.imageID = imageID;
        this.preferredTileWidth = preferredTileWidth;
        this.preferredTileHeight = preferredTileHeight;
        this.jpegQuality = jpegQuality;
    }

    @Override
    public BufferedImage readTile(TileRequest tileRequest) throws IOException {
        logger.debug("Reading tile {} from web API", tileRequest);

        try {
            return apisHandler.readTile(
                    imageID,
                    tileRequest,
                    preferredTileWidth,
                    preferredTileHeight,
                    jpegQuality
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() {}

    @Override
    public String toString() {
        return String.format("Web reader of image with ID %d", imageID);
    }
}
