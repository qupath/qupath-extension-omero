package qupath.ext.omero.core.apis.webgateway;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonObject;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableIntegerValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.image.ChannelSettings;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;
import qupath.ext.omero.core.RequestSender;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * API to communicate with a OMERO.gateway server.
 * <p>
 * This API is used to retrieve several icons, image thumbnails and
 * provides access to the pixels of JPEG-compressed RGB encoded images.
 */
public class WebGatewayApi {

    private static final Logger logger = LoggerFactory.getLogger(WebGatewayApi.class);
    private static final int THUMBNAIL_CACHE_SIZE = 1000;
    private static final int METADATA_CACHE_SIZE = 50;
    private static final String ICON_URL = "%s/static/webgateway/img/%s";
    private static final String PROJECT_ICON_NAME = "folder16.png";
    private static final String DATASET_ICON_NAME = "folder_image16.png";
    private static final String ORPHANED_FOLDER_ICON_NAME = "folder_yellow16.png";
    private static final String WELL_ICON_NAME = "icon_folder.png";
    private static final String THUMBNAIL_URL = "%s/webgateway/render_thumbnail/%d/%d";
    private static final String IMAGE_DATA_URL = "%s/webgateway/imgData/%d";
    private static final String TILE_URL = "%s/webgateway/render_image_region/%d/%d/%d/?" +
            "tile=%d,%d,%d,%d,%d" +
            "&c=%s" +
            "&m=c" +
            "&q=%f";
    private static final String TILE_CHANNEL_PARAMETER = URLEncoder.encode("1|0:255$FF0000,2|0:255$00FF00,3|0:255$0000FF", StandardCharsets.UTF_8);
    private static final String CHANGE_CHANNEL_DISPLAY_RANGES_AND_COLORS_URL = "%s/webgateway/saveImgRDef/%d/?m=c&c=%s";
    private final IntegerProperty numberOfThumbnailsLoading = new SimpleIntegerProperty(0);
    private final URI webServerUri;
    private final RequestSender requestSender;
    private final String token;
    private final LoadingCache<IdSizeWrapper, BufferedImage> thumbnailsCache;
    private final LoadingCache<Long, ImageServerMetadata> metadataCache;
    private record IdSizeWrapper(long id, int size) {}

    /**
     * Creates a web gateway client.
     *
     * @param webServerUri the URL to the OMERO web server to connect to
     * @param requestSender the request sender to use
     * @param token the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a>
     *              used by this session. This is needed to perform some functions of this API.
     */
    public WebGatewayApi(URI webServerUri, RequestSender requestSender, String token) {
        this.webServerUri = webServerUri;
        this.requestSender = requestSender;
        this.token = token;

        this.thumbnailsCache = CacheBuilder.newBuilder()
                .maximumSize(THUMBNAIL_CACHE_SIZE)
                .build(new CacheLoader<>() {
                    @Override
                    public BufferedImage load(IdSizeWrapper idSizeWrapper) throws ExecutionException, InterruptedException, URISyntaxException {
                        logger.debug("Fetching thumbnail with ID {} and size {} (not already in cache)", idSizeWrapper.id(), idSizeWrapper.size());

                        synchronized (this) {
                            numberOfThumbnailsLoading.set(numberOfThumbnailsLoading.get() + 1);
                        }

                        return requestSender.getImage(new URI(String.format(THUMBNAIL_URL, webServerUri, idSizeWrapper.id(), idSizeWrapper.size())))
                                .whenComplete((thumbnail, error) -> {
                                    synchronized (this) {
                                        numberOfThumbnailsLoading.set(numberOfThumbnailsLoading.get() - 1);
                                    }
                                }).get();
                    }
                });
        this.metadataCache = CacheBuilder.newBuilder()
                .maximumSize(METADATA_CACHE_SIZE)
                .build(new CacheLoader<>() {
                    @Override
                    public ImageServerMetadata load(Long imageId) throws ExecutionException, InterruptedException, URISyntaxException {
                        logger.debug("Fetching metadata of image with ID {} (not already in cache)", imageId);

                        return requestSender.getAndConvert(new URI(String.format(IMAGE_DATA_URL, webServerUri, imageId)), JsonObject.class)
                                .thenApplyAsync(ImageMetadataResponseParser::createMetadataFromJson)
                                .get();
                    }
                });
    }

    @Override
    public String toString() {
        return String.format("WebGateway API of %s", webServerUri);
    }

    /**
     * @return the number of thumbnails currently being loaded.
     * This property may be updated from any thread
     */
    public ObservableIntegerValue getNumberOfThumbnailsLoading() {
        return numberOfThumbnailsLoading;
    }

    /**
     * Attempt to retrieve the OMERO project icon.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with the project icon
     */
    public CompletableFuture<BufferedImage> getProjectIcon() {
        logger.debug("Getting OMERO project icon");

        try {
            return requestSender.getImage(new URI(String.format(ICON_URL, webServerUri, PROJECT_ICON_NAME)));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Attempt to retrieve the OMERO dataset icon.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with the dataset icon
     */
    public CompletableFuture<BufferedImage> getDatasetIcon() {
        logger.debug("Getting OMERO dataset icon");

        try {
            return requestSender.getImage(new URI(String.format(ICON_URL, webServerUri, DATASET_ICON_NAME)));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Attempt to retrieve the OMERO orphaned folder icon.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with the orphaned folder icon
     */
    public CompletableFuture<BufferedImage> getOrphanedFolderIcon() {
        logger.debug("Getting OMERO orphaned folder icon");

        try {
            return requestSender.getImage(new URI(String.format(ICON_URL, webServerUri, ORPHANED_FOLDER_ICON_NAME)));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Attempt to retrieve the OMERO well icon.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with the well icon
     */
    public CompletableFuture<BufferedImage> getWellIcon() {
        logger.debug("Getting OMERO well icon");

        try {
            return requestSender.getImage(new URI(String.format(ICON_URL, webServerUri, WELL_ICON_NAME)));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Attempt to retrieve the thumbnail of an image.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     * <p>
     * Thumbnails are cached in a cache of size {@link #THUMBNAIL_CACHE_SIZE}.
     *
     * @param imageId the OMERO image ID
     * @param size the max width and max height the thumbnail should have
     * @return a CompletableFuture (that may complete exceptionally) with the thumbnail
     */
    public CompletableFuture<BufferedImage> getThumbnail(long imageId, int size) {
        logger.debug("Getting thumbnail of image with ID {} and with size {}", imageId, size);

        return CompletableFuture.supplyAsync(() -> thumbnailsCache.getUnchecked(new IdSizeWrapper(imageId, size)));
    }

    /**
     * Attempt to retrieve the metadata of an image.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     * <p>
     * Metadata are cached in a cache of size {@link #METADATA_CACHE_SIZE}.
     *
     * @param imageId the OMERO image ID
     * @return a CompletableFuture (that may complete exceptionally) with the metadata
     */
    public CompletableFuture<ImageServerMetadata> getImageMetadata(long imageId) {
        logger.debug("Getting metadata of image with ID {}", imageId);

        return CompletableFuture.supplyAsync(() -> metadataCache.getUnchecked(imageId));
    }

    /**
     * Attempt to read a tile (portion of image).
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request or the conversion failed for example).
     *
     * @param imageId the OMERO image ID
     * @param tileRequest the tile request (usually coming from the {@link qupath.lib.images.servers.AbstractTileableImageServer AbstractTileableImageServer})
     * @param preferredTileWidth the preferred tile width in pixels
     * @param preferredTileHeight the preferred tile height in pixels
     * @param quality the JPEG quality, from 0 to 1
     * @return a CompletableFuture (that may complete exceptionally) with the tile
     */
    public CompletableFuture<BufferedImage> readTile(long imageId, TileRequest tileRequest, int preferredTileWidth, int preferredTileHeight, double quality) {
        logger.debug("Reading tile {} of image with ID {} and JPEG quality {}", tileRequest, imageId, quality);

        try {
            return requestSender.getImage(new URI(String.format(TILE_URL,
                    webServerUri, imageId, tileRequest.getZ(), tileRequest.getT(),
                    tileRequest.getLevel(), tileRequest.getTileX() / preferredTileWidth, tileRequest.getTileY() / preferredTileHeight,
                    preferredTileWidth, preferredTileHeight,
                    TILE_CHANNEL_PARAMETER,
                    quality
            )));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Attempt to change the channel colors of an image.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request or the conversion failed for example).
     *
     * @param imageId the ID of the image to change the channel settings
     * @param newChannelColors the new channel colors, with the packed RGB format
     * @param existingChannelSettings the existing channel settings of the image
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     * @throws IllegalArgumentException when {@code newChannelColors} and {@code existingChannelSettings}
     * don't have the same number of elements
     */
    public CompletableFuture<Void> changeChannelColors(long imageId, List<Integer> newChannelColors, List<ChannelSettings> existingChannelSettings) {
        logger.debug("Changing channel colors of image with ID {} from {} to {}", imageId, existingChannelSettings, newChannelColors);

        if (newChannelColors.size() != existingChannelSettings.size()) {
            throw new IllegalArgumentException(String.format(
                    "The provided number of new channel colors (%d) doesn't match with the provided existing number of channels (%d)",
                    newChannelColors.size(),
                    existingChannelSettings.size()
            ));
        }

        return changeChannelDisplayRangesAndColors(
                imageId,
                IntStream.range(0, existingChannelSettings.size())
                        .mapToObj(i -> new ChannelSettings(
                                existingChannelSettings.get(i).minDisplayRange(),
                                existingChannelSettings.get(i).maxDisplayRange(),
                                newChannelColors.get(i)
                        ))
                        .toList()
        );
    }

    /**
     * Attempt to change the channel display ranges of an image.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request or the conversion failed for example).
     *
     * @param imageId the ID of the image to change the channel settings
     * @param newChannelSettings the new channel display ranges (other fields of {@link ChannelSettings}
     *                         will be ignored)
     * @param existingChannelSettings the existing channel settings of the image
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     * @throws IllegalArgumentException when {@code newChannelSettings} and {@code existingChannelSettings}
     * don't have the same number of elements
     */
    public CompletableFuture<Void> changeChannelDisplayRanges(long imageId, List<ChannelSettings> newChannelSettings, List<ChannelSettings> existingChannelSettings) {
        logger.debug("Changing channel display ranges of image with ID {} from {} to {}", imageId, existingChannelSettings, newChannelSettings);

        if (newChannelSettings.size() != existingChannelSettings.size()) {
            throw new IllegalArgumentException(String.format(
                    "The provided number of new channel settings (%d) doesn't match with the provided existing number of channels (%d)",
                    newChannelSettings.size(),
                    existingChannelSettings.size()
            ));
        }

        return changeChannelDisplayRangesAndColors(
                imageId,
                IntStream.range(0, existingChannelSettings.size())
                        .mapToObj(i -> new ChannelSettings(
                                newChannelSettings.get(i).minDisplayRange(),
                                newChannelSettings.get(i).maxDisplayRange(),
                                existingChannelSettings.get(i).rgbColor()
                        ))
                        .toList()
        );
    }

    private CompletableFuture<Void> changeChannelDisplayRangesAndColors(long imageId, List<ChannelSettings> channelSettings) {
        URI uri;
        try {
            uri = new URI(String.format(
                    CHANGE_CHANNEL_DISPLAY_RANGES_AND_COLORS_URL,
                    webServerUri,
                    imageId,
                    URLEncoder.encode(
                            IntStream
                                    .range(0, channelSettings.size())
                                    .mapToObj(i -> String.format(
                                            "%d|%f:%f$%s",
                                            i + 1,
                                            channelSettings.get(i).minDisplayRange(),
                                            channelSettings.get(i).maxDisplayRange(),
                                            String.format(
                                                    "%02X%02X%02X",
                                                    ColorTools.unpackRGB(channelSettings.get(i).rgbColor())[0],
                                                    ColorTools.unpackRGB(channelSettings.get(i).rgbColor())[1],
                                                    ColorTools.unpackRGB(channelSettings.get(i).rgbColor())[2]
                                            )
                                    ))
                                    .collect(Collectors.joining(",")),
                            StandardCharsets.UTF_8
                    )
            ));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        String referer = String.format("%s/iviewer/?images=%d", webServerUri, imageId);
        logger.debug("Changing channel display ranges and colors of image with ID {} with referer {}", imageId, referer);

        return requestSender.post(
                uri,
                "",
                referer,
                token
        ).thenAccept(response -> {
            if (!response.equals("true")) {
                throw new RuntimeException(String.format("Change channel display ranges and colors response %s not true", response));
            }
        });
    }
}
