package qupath.ext.omero.core.apis.iviewer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.commonentities.shapes.Shape;
import qupath.ext.omero.core.apis.iviewer.imageentities.ImageData;
import qupath.ext.omero.core.apis.iviewer.imageentities.OmeroImageData;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.lib.common.ThreadTools;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * API to communicate with an <a href="https://www.openmicroscopy.org/omero/iviewer/">OMERO.iviewer</a>.
 * <p>
 * It is simply used to send ROIs and retrieve metadata from an OMERO server.
 * <p>
 * An instance of this class must be {@link #close() closed} once no longer used.
 */
public class IViewerApi implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(IViewerApi.class);
    private static final String ROIS_URL = "%s/iviewer/persist_rois/";
    private static final String ROIS_BODY = """
        {
            "imageId":%d,
            "rois": {
                "count":%d,
                "empty_rois":{%s},
                "new_and_deleted":[],
                "deleted":{},
                "new":[%s],
                "modified":[]
            }
        }
        """;
    private static final String ROIS_REFERER_URL = "%s/iviewer/?images=%d";
    private static final String IMAGE_SETTINGS_URL = "%s/iviewer/image_data/%d/";
    private final ExecutorService executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            ThreadTools.createThreadFactory("iviewer-api-", true)
    );
    private final URI webServerUri;
    private final RequestSender requestSender;
    private final String token;

    /**
     * Creates an iviewer client.
     *
     * @param webServerUri the URL to the OMERO web server to connect to
     * @param requestSender the request sender to use
     * @param token the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a>
     *              used by this session. This is needed to perform some functions of this API.
     */
    public IViewerApi(URI webServerUri, RequestSender requestSender, String token) {
        this.webServerUri = webServerUri;
        this.requestSender = requestSender;
        this.token = token;
    }

    @Override
    public void close() throws Exception {
        executorService.close();
    }

    @Override
    public String toString() {
        return String.format("IViewer API of %s", webServerUri);
    }

    /**
     * Attempt to add shapes to the provided image on the server.
     * <p>
     * An attempt is made to send the provided shapes in batches, so that the size of each request body does not exceed
     * {@link PreferencesManager#getMaxBodySizeBytes(URI)}. However, if a single shape has a size bigger than
     * {@link PreferencesManager#getMaxBodySizeBytes(URI)}, then the size of the body of the corresponding request
     * will be bigger than {@link PreferencesManager#getMaxBodySizeBytes(URI)}.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param imageId the OMERO image id
     * @param shapesToAdd the list of shapes to add
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     */
    public CompletableFuture<Void> addShapes(long imageId, List<? extends Shape> shapesToAdd) {
        logger.debug("Adding shapes {} to image with ID {}", shapesToAdd, imageId);

        if (shapesToAdd.isEmpty()) {
            logger.debug("No shapes to add to image with ID {}. Returning without sending request", imageId);
            return CompletableFuture.completedFuture(null);
        }

        URI uri;
        try {
            uri = new URI(String.format(ROIS_URL, webServerUri));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        List<List<String>> roiBatches = BatchCalculator.splitObjectsIntoBatches(
                shapesToAdd.stream().map(Shape::createJson).toList(),
                PreferencesManager.getMaxBodySizeBytes(webServerUri),
                rois -> (long) String.format(
                        ROIS_BODY,
                        imageId,
                        rois.size(),
                        "",
                        String.join(", ", rois)
                ).getBytes(StandardCharsets.UTF_8).length
        );
        logger.debug("Created batches {} for shapes {}", roiBatches, shapesToAdd);

        return CompletableFuture.runAsync(
                () -> {
                    String referer = String.format(ROIS_REFERER_URL, webServerUri, imageId);

                    for (List<String> roiBatch: roiBatches) {
                        String body = String.format(
                                ROIS_BODY,
                                imageId,
                                roiBatch.size(),
                                "",
                                String.join(", ", roiBatch)
                        );
                        logger.debug("Sending body {} with referer {} to add shapes {} to image with ID {}", body, referer, roiBatch, imageId);

                        requestSender.post(
                                uri,
                                body,
                                referer,
                                token
                        ).thenAccept(response -> {
                            if (response.toLowerCase().contains("error")) {
                                throw new RuntimeException(String.format("Error when adding shapes: %s", response));
                            }
                        }).join();
                    }
                },
                executorService
        );
    }

    /**
     * Attempt to delete shapes from the provided image on the server.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param imageId the OMERO image id
     * @param shapesToRemove the list of shapes to remove
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     */
    public CompletableFuture<Void> deleteShapes(long imageId, List<Shape> shapesToRemove) {
        logger.debug("Removing shapes {} from image with ID {}", shapesToRemove, imageId);

        URI uri;
        try {
            uri = new URI(String.format(ROIS_URL, webServerUri));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        String body = String.format(
                ROIS_BODY,
                imageId,
                shapesToRemove.size(),
                shapesToRemove.stream()
                        .map(shape -> String.format("\"%s\":[\"%s\"]", shape.getOldId().split(":")[0], shape.getOldId()))
                        .collect(Collectors.joining(",")),
                ""
        );
        String referer = String.format(ROIS_REFERER_URL, webServerUri, imageId);
        logger.debug("Sending body {} with referer {} to remove shapes {} from image with ID {}", body, referer, shapesToRemove, imageId);

        return requestSender.post(
                uri,
                body,
                referer,
                token
        ).thenAccept(response -> {
            if (response.toLowerCase().contains("error")) {
                throw new RuntimeException(String.format("Error when sending shapes: %s", response));
            }
        });
    }

    /**
     * Attempt to retrieve some information about an image.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param imageId the id of the image whose settings should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the retrieved image settings
     */
    public CompletableFuture<ImageData> getImageData(long imageId) {
        logger.debug("Getting image settings of image with ID {}", imageId);

        try {
            return requestSender.getAndConvert(
                    new URI(String.format(IMAGE_SETTINGS_URL, webServerUri, imageId)),
                    OmeroImageData.class
            ).thenApply(ImageData::new);
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
