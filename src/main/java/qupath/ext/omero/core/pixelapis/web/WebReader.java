package qupath.ext.omero.core.pixelapis.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.lib.images.servers.ImageServerMetadata;
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
    private static final int MAX_WEB_GATEWAY_SIZE = 1024;
    private final ApisHandler apisHandler;
    private final long imageID;
    private final double jpegQuality;
    private int preferredTileWidth;
    private int preferredTileHeight;

    /**
     * Creates a new WebApi.
     *
     * @param apisHandler the request handler which will be used to perform web requests
     * @param imageID the ID of the image to open
     * @param preferredTileWidth the preferred tile width of the image to open in pixels
     * @param preferredTileHeight the preferred tile height of the image to open in pixels
     * @param jpegQuality the JPEG quality of the image to open (between 0 and 1)
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
    public ImageServerMetadata updateMetadata(ImageServerMetadata originalMetadata) {
        logger.debug("Updating metadata from web reader");

        return maxWebGatewayTileSize(originalMetadata);
    }

    @Override
    public void close() {}

    @Override
    public String toString() {
        return String.format("Web reader of image with ID %d", imageID);
    }

    /**
     * The OMERO webgateway used to retrieve tiles cannot accept tiles with width or height greater than {@link #MAX_WEB_GATEWAY_SIZE}
     * (see <a href="https://github.com/ome/omero-web/issues/609">this issue</a>). This function fixes that by
     * setting the maximum tile width/height to be {@link #MAX_WEB_GATEWAY_SIZE}.
     *
     * @param originalMetadata the original metadata of the image
     * @return a modified (or not) version of the provided metadata with the tile size fix
     */
    private synchronized ImageServerMetadata maxWebGatewayTileSize(ImageServerMetadata originalMetadata) {
        if (originalMetadata.getPreferredTileWidth() <= MAX_WEB_GATEWAY_SIZE && originalMetadata.getPreferredTileHeight() <= MAX_WEB_GATEWAY_SIZE) {
            return originalMetadata;
        }

        preferredTileWidth = Math.min(originalMetadata.getPreferredTileWidth(), MAX_WEB_GATEWAY_SIZE);
        preferredTileHeight = Math.min(originalMetadata.getPreferredTileHeight(), MAX_WEB_GATEWAY_SIZE);

        logger.debug(
                "Original preferred tile width ({}) or height ({}) greater than max allowed ({}). Updating to ({}, {})",
                originalMetadata.getPreferredTileWidth(),
                originalMetadata.getPreferredTileHeight(),
                MAX_WEB_GATEWAY_SIZE,
                preferredTileWidth,
                preferredTileHeight
        );

        return new ImageServerMetadata.Builder(originalMetadata)
                .preferredTileSize(preferredTileWidth, preferredTileHeight)
                .build();
    }
}
