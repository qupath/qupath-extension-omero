package qupath.ext.omero.core.apis;

import com.google.gson.Gson;
import qupath.ext.omero.core.entities.image.ImageSettings;
import qupath.ext.omero.core.entities.shapes.Shape;
import qupath.ext.omero.core.RequestSender;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * <p>API to communicate with an <a href="https://www.openmicroscopy.org/omero/iviewer/">OMERO.iviewer</a>.</p>
 * <p>It is simply used to send ROIs to an OMERO server.</p>
 */
class IViewerApi {

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
    private final URI host;

    /**
     * Creates an iviewer client.
     *
     * @param host  the base server URI (e.g. <a href="https://idr.openmicroscopy.org">https://idr.openmicroscopy.org</a>)
     */
    public IViewerApi(URI host) {
        this.host = host;
    }

    @Override
    public String toString() {
        return String.format("IViewer API of %s", host);
    }

    /**
     * <p>
     *     Attempt to write and delete ROIs to the server.
     * </p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param id the OMERO image id
     * @param shapesToAdd the list of shapes to add
     * @param shapesToRemove the list of shapes to remove
     * @param token the OMERO <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a>
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     */
    public CompletableFuture<Void> writeROIs(long id, Collection<Shape> shapesToAdd, Collection<Shape> shapesToRemove, String token) {
        URI uri;
        try {
            uri = new URI(String.format(ROIS_URL, host));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        Gson gson = new Gson();
        List<String> roisToAdd = shapesToAdd.stream().map(gson::toJson).toList();
        String roisToRemove = shapesToRemove.stream()
                .map(shape -> String.format("\"%s\":[\"%s\"]", shape.getOldId().split(":")[0], shape.getOldId()))
                .collect(Collectors.joining(","));

        return RequestSender.post(
                uri,
                String.format(
                        ROIS_BODY,
                        id,
                        roisToAdd.size() + shapesToRemove.size(),
                        roisToRemove,
                        String.join(", ", roisToAdd)
                ),
                String.format(ROIS_REFERER_URL, host, id),
                token
        ).thenAccept(response -> {
            if (response.toLowerCase().contains("error")) {
                throw new RuntimeException(String.format("Error when sending ROIs: %s", response));
            }
        });
    }

    /**
     * <p>
     *     Attempt to retrieve the settings of an image.
     * </p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param imageId the id of the image whose settings should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the retrieved image settings
     */
    public CompletableFuture<ImageSettings> getImageSettings(long imageId) {
        try {
            return RequestSender.getAndConvert(
                    new URI(String.format(IMAGE_SETTINGS_URL, host, imageId)),
                    ImageSettings.class
            );
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
