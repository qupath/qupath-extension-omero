package qupath.ext.omero.core.apis;

import com.google.gson.JsonObject;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.image.ChannelSettings;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.TileRequest;
import qupath.ext.omero.core.entities.imagemetadata.ImageMetadataResponse;
import qupath.ext.omero.core.WebUtilities;
import qupath.ext.omero.core.RequestSender;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>API to communicate with a OMERO.gateway server.</p>
 * <p>
 *     This API is used to retrieve several icons, image thumbnails and
 *     provides access to the pixels of JPEG-compressed RGB encoded images.
 * </p>
 */
class WebGatewayApi {

    private static final Logger logger = LoggerFactory.getLogger(WebGatewayApi.class);
    private static final String ICON_URL = "%s/static/webgateway/img/%s";
    private static final String PROJECT_ICON_NAME = "folder16.png";
    private static final String DATASET_ICON_NAME = "folder_image16.png";
    private static final String ORPHANED_FOLDER_ICON_NAME = "folder_yellow16.png";
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
    private final URI host;
    private final String token;

    /**
     * Creates a web gateway client.
     *
     * @param host  the base server URI (e.g. <a href="https://idr.openmicroscopy.org">https://idr.openmicroscopy.org</a>)
     * @param token  the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a>
     *               used by this session. This is needed to perform some functions of this API.
     */
    public WebGatewayApi(URI host, String token) {
        this.host = host;
        this.token = token;
    }

    @Override
    public String toString() {
        return String.format("WebGateway API of %s", host);
    }

    /**
     * @return the number of thumbnails currently being loaded.
     * This property may be updated from any thread
     */
    public ReadOnlyIntegerProperty getNumberOfThumbnailsLoading() {
        return numberOfThumbnailsLoading;
    }

    /**
     * <p>Attempt to retrieve the OMERO project icon.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the project icon, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> getProjectIcon() {
        return ApiUtilities.getImage(String.format(ICON_URL, host, PROJECT_ICON_NAME));
    }

    /**
     * <p>Attempt to retrieve the OMERO dataset icon.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the dataset icon, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> getDatasetIcon() {
        return ApiUtilities.getImage(String.format(ICON_URL, host, DATASET_ICON_NAME));
    }

    /**
     * <p>Attempt to retrieve the OMERO orphaned folder icon.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the orphaned folder icon, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> getOrphanedFolderIcon() {
        return ApiUtilities.getImage(String.format(ICON_URL, host, ORPHANED_FOLDER_ICON_NAME));
    }

    /**
     * <p>Attempt to retrieve the thumbnail of an image.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param id  the OMERO image ID
     * @param size  the max width and max height the thumbnail should have
     * @return a CompletableFuture with the thumbnail, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> getThumbnail(long id, int size) {
        changeNumberOfThumbnailsLoading(true);

        return ApiUtilities.getImage(String.format(THUMBNAIL_URL, host, id, size)).thenApply(thumbnail -> {
            changeNumberOfThumbnailsLoading(false);
            return thumbnail;
        });
    }

    /**
     * <p>Attempt to retrieve the metadata of an image.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param id  the OMERO image ID
     * @return a CompletableFuture with the metadata, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<ImageMetadataResponse>> getImageMetadata(long id) {
        var uri = WebUtilities.createURI(String.format(IMAGE_DATA_URL, host, id));

        if (uri.isPresent()) {
            return RequestSender.getAndConvert(uri.get(), JsonObject.class).thenApply(jsonObject -> {
                if (jsonObject.isPresent()) {
                    return ImageMetadataResponse.createFromJson(jsonObject.get());
                } else {
                    return Optional.empty();
                }
            });
        } else {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /**
     * <p>Attempt to read a tile (portion of image).</p>
     * <p>This function is asynchronous.</p>
     *
     * @param id  the OMERO image ID
     * @param tileRequest  the tile request (usually coming from the {@link qupath.lib.images.servers.AbstractTileableImageServer AbstractTileableImageServer})
     * @param preferredTileWidth  the preferred tile width in pixels
     * @param preferredTileHeight  the preferred tile height in pixels
     * @param quality  the JPEG quality, from 0 to 1
     * @return a CompletableFuture with the tile, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> readTile(long id, TileRequest tileRequest, int preferredTileWidth, int preferredTileHeight, double quality) {
        return ApiUtilities.getImage(String.format(TILE_URL,
                host, id, tileRequest.getZ(), tileRequest.getT(),
                tileRequest.getLevel(), tileRequest.getTileX() / preferredTileWidth, tileRequest.getTileY() / preferredTileHeight,
                preferredTileWidth, preferredTileHeight,
                TILE_CHANNEL_PARAMETER,
                quality
        ));
    }

    /**
     * <p>
     *     Attempt to change the channel colors of an image.
     * </p>
     * <p>This function is asynchronous.</p>
     *
     * @param imageID  the ID of the image to change the channel settings
     * @param newChannelColors  the new channel colors, with the packed RGB format
     * @param existingChannelSettings  the existing channel settings of the image
     * @return a CompletableFuture indicating the success of the operation
     */
    public CompletableFuture<Boolean> changeChannelColors(long imageID, List<Integer> newChannelColors, List<ChannelSettings> existingChannelSettings) {
        if (newChannelColors.size() == existingChannelSettings.size()) {
            return changeChannelDisplayRangesAndColors(
                    imageID,
                    IntStream.range(0, existingChannelSettings.size())
                            .mapToObj(i -> new ChannelSettings(
                                    existingChannelSettings.get(i).getMinDisplayRange(),
                                    existingChannelSettings.get(i).getMaxDisplayRange(),
                                    newChannelColors.get(i)
                            ))
                            .toList()
            );
        } else {
            logger.warn("The provided number of new channel colors doesn't match with the existing number of channels");
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * <p>
     *     Attempt to change the channel display ranges of an image.
     * </p>
     * <p>This function is asynchronous.</p>
     *
     * @param imageID  the ID of the image to change the channel settings
     * @param newChannelSettings  the new channel display ranges (other fields of {@link ChannelSettings}
     *                         will be ignored)
     * @param existingChannelSettings  the existing channel settings of the image
     * @return a CompletableFuture indicating the success of the operation
     */
    public CompletableFuture<Boolean> changeChannelDisplayRanges(long imageID, List<ChannelSettings> newChannelSettings, List<ChannelSettings> existingChannelSettings) {
        if (newChannelSettings.size() == existingChannelSettings.size()) {
            return changeChannelDisplayRangesAndColors(
                    imageID,
                    IntStream.range(0, existingChannelSettings.size())
                            .mapToObj(i -> new ChannelSettings(
                                    newChannelSettings.get(i).getMinDisplayRange(),
                                    newChannelSettings.get(i).getMaxDisplayRange(),
                                    existingChannelSettings.get(i).getRgbColor()
                            ))
                            .toList()
            );
        } else {
            logger.warn("The provided number of new channel settings doesn't match with the existing number of channels");
            return CompletableFuture.completedFuture(false);
        }
    }

    private synchronized void changeNumberOfThumbnailsLoading(boolean increment) {
        int quantityToAdd = increment ? 1 : -1;
        numberOfThumbnailsLoading.set(numberOfThumbnailsLoading.get() + quantityToAdd);
    }

    private CompletableFuture<Boolean> changeChannelDisplayRangesAndColors(long imageID, List<ChannelSettings> channelSettings) {
        var uri = WebUtilities.createURI(String.format(
                CHANGE_CHANNEL_DISPLAY_RANGES_AND_COLORS_URL,
                host,
                imageID,
                URLEncoder.encode(
                        IntStream
                                .range(0, channelSettings.size())
                                .mapToObj(i -> String.format(
                                        "%d|%f:%f$%s",
                                        i + 1,
                                        channelSettings.get(i).getMinDisplayRange(),
                                        channelSettings.get(i).getMaxDisplayRange(),
                                        String.format(
                                                "%02X%02X%02X",
                                                ColorTools.unpackRGB(channelSettings.get(i).getRgbColor())[0],
                                                ColorTools.unpackRGB(channelSettings.get(i).getRgbColor())[1],
                                                ColorTools.unpackRGB(channelSettings.get(i).getRgbColor())[2]
                                        )
                                ))
                                .collect(Collectors.joining(",")),
                        StandardCharsets.UTF_8
                )
        ));

        if (uri.isPresent()) {
            return RequestSender.post(
                    uri.get(),
                    "",
                    String.format("%s/iviewer/?images=%d", host, imageID),
                    token
            ).thenApply(response -> response.isPresent() && response.get().equals("true"));
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }
}
