package qupath.ext.omero.core.apis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.Namespace;
import qupath.ext.omero.core.entities.annotations.Annotation;
import qupath.ext.omero.core.entities.annotations.AnnotationGroup;
import qupath.ext.omero.core.entities.annotations.FileAnnotation;
import qupath.ext.omero.core.entities.annotations.MapAnnotation;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.search.SearchQuery;
import qupath.ext.omero.core.entities.search.SearchResult;
import qupath.ext.omero.core.RequestSender;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * API to communicate with a OMERO.web server.
 * <p>
 * This API is mainly used to keep a connection alive, log out, perform a search
 * and get OMERO annotations.
 * <p>
 * An instance of this class must be {@link #close() closed} once no longer used.
 */
class WebclientApi implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(WebclientApi.class);
    private static final String PING_URL = "%s/webclient/keepalive_ping/";
    private static final String ITEM_URL = "%s/webclient/?show=%s-%d";
    private static final String LOGOUT_URL = "%s/webclient/logout/";
    private static final String ORPHANED_IMAGES_URL = "%s/webclient/api/images/?orphaned=true";
    private static final String WEBCLIENT_URL = "%s/webclient/";
    private static final Pattern USER_ID_PATTERN = Pattern.compile("WEBCLIENT.USER = \\{'id': (.+?), 'fullName':");
    private static final String READ_ANNOTATION_URL = "%s/webclient/api/annotations/?%s=%d";
    private static final String SEARCH_URL = "%s/webclient/load_searching/form/" +
            "?query=%s&%s&%s&searchGroup=%s&ownedBy=%s" +
            "&useAcquisitionDate=false&startdateinput=&enddateinput=&_=%d";
    private static final String WRITE_KEY_VALUES_URL = "%s/webclient/annotate_map/";
    private static final String WRITE_NAME_URL = "%s/webclient/action/savename/image/%d/";
    private static final String WRITE_CHANNEL_NAMES_URL = "%s/webclient/edit_channel_names/%d/";
    private static final String SEND_ATTACHMENT_URL = "%s/webclient/annotate_file/";
    private static final String QUPATH_FILE_IDENTIFIER = "qupath_";
    private static final String DELETE_ATTACHMENT_URL = "%s/webclient/action/delete/file/%d/";
    private static final String IMAGE_ICON_URL = "%s/static/webclient/image/image16.png";
    private static final String SCREEN_ICON_URL = "%s/static/webclient/image/folder_screen16.png";
    private static final String PLATE_ICON_URL = "%s/static/webclient/image/folder_plate16.png";
    private static final String PLATE_ACQUISITION_ICON_URL = "%s/static/webclient/image/run16.png";
    private static final Map<Class<? extends ServerEntity>, String> TYPE_TO_URI_LABEL = Map.of(
            Image.class, "image",
            Dataset.class, "dataset",
            Project.class, "project",
            Screen.class, "screen",
            Plate.class, "plate",
            PlateAcquisition.class, "run"
    );
    private static final Gson gson = new Gson();
    private final URI host;
    private final RequestSender requestSender;
    private final URI pingUri;
    private final String token;

    /**
     * Creates a web client.
     *
     * @param host the base server URI (e.g. <a href="https://idr.openmicroscopy.org">https://idr.openmicroscopy.org</a>)
     * @param requestSender the request sender to use when making requests
     * @param token the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a>
     *              used by this session. This is needed to properly close this API.
     * @throws IllegalArgumentException when the ping URI cannot be created from the host
     */
    public WebclientApi(URI host, RequestSender requestSender, String token) {
        this.host = host;
        this.requestSender = requestSender;
        this.token = token;

        pingUri = URI.create(String.format(PING_URL, host));
    }

    /**
     * Close the connection by sending a login out request.
     * This may take a moment as it waits for the request to complete.
     *
     * @throws URISyntaxException when the login out request URI is invalid
     * @throws ExecutionException when the request failed
     * @throws InterruptedException when the wait for the request to end was interrupted
     */
    @Override
    public void close() throws URISyntaxException, ExecutionException, InterruptedException {
        logger.debug("Sending login out request");

        URI uri = new URI(String.format(LOGOUT_URL, host));

        requestSender.post(
                uri,
                String.format("csrfmiddlewaretoken=%s", token).getBytes(StandardCharsets.UTF_8),
                uri.toString(),
                token
        ).get();
    }

    @Override
    public String toString() {
        return String.format("Webclient API of %s", host);
    }

    /**
     * Returns a link of the OMERO.web client pointing to a server entity.
     *
     * @param entity the entity to have a link to.
     *               Must be an {@link Image}, {@link Dataset}, {@link Project},
     *               {@link Screen}, {@link Plate} or {@link PlateAcquisition}
     * @return a URL pointing to the server entity
     * @throws IllegalArgumentException when the provided entity is not an image, dataset, project,
     * screen, plate, or plate acquisition
     */
    public String getEntityURI(ServerEntity entity) {
        if (!TYPE_TO_URI_LABEL.containsKey(entity.getClass())) {
            throw new IllegalArgumentException(String.format(
                    "The provided item (%s) is not an image, dataset, project, screen, plate, or plate acquisition.",
                    entity
            ));
        }

        return String.format(
                ITEM_URL,
                host,
                TYPE_TO_URI_LABEL.get(entity.getClass()),
                entity.getId()
        );
    }

    /**
     * Attempt to send a ping to the server. This is needed to keep the connection alive between the client
     * and the server.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @return a void CompletableFuture (that completes exceptionally if the ping fails)
     */
    public CompletableFuture<Void> ping() {
        logger.debug("Sending ping");

        return requestSender.isLinkReachable(pingUri, RequestSender.RequestType.GET, true, false);
    }

    /**
     * Attempt to get the image IDs of all orphaned images of the server visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request or the conversion failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with a list containing the ID of all orphaned images
     */
    public CompletableFuture<List<Long>> getOrphanedImagesIds() {
        logger.debug("Getting the IDs of all orphaned images of the current user");

        URI uri;
        try {
            uri = new URI(String.format(ORPHANED_IMAGES_URL, host));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.getAndConvertToJsonList(uri, "images").thenApply(elements ->
                elements.stream()
                        .map(jsonElement -> {
                            if (jsonElement.isJsonObject() && jsonElement.getAsJsonObject().has("id")) {
                                return Long.parseLong(jsonElement.getAsJsonObject().get("id").toString());
                            } else {
                                throw new IllegalArgumentException(String.format(
                                        "'id' not found in %s", jsonElement
                                ));
                            }
                        })
                        .toList()
        );
    }

    /**
     * Attempt to get the ID of the public user of the server.
     * This only works if there is no active authenticated connection with the server.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request or the conversion failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with the public user ID
     */
    public CompletableFuture<Long> getPublicUserId() {
        logger.debug("Getting ID of the public user of the server");

        URI uri;
        try {
            uri = new URI(String.format(WEBCLIENT_URL, host));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.get(uri).thenApply(response -> {
            Matcher matcher = USER_ID_PATTERN.matcher(response);

            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            } else {
                throw new RuntimeException(String.format("Pattern %s not found in %s", USER_ID_PATTERN, response));
            }
        });
    }

    /**
     * Attempt to retrieve OMERO annotations of an OMERO entity.
     * An OMERO annotation is <b>not</b> similar to a QuPath annotation, it refers to some metadata
     * attached to an entity.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param entityId the ID of the entity
     * @param entityClass the class of the entity whose annotation should be retrieved.
     *                    Must be an {@link Image}, {@link Dataset}, {@link Project},
     *                    {@link Screen}, {@link Plate}, or {@link PlateAcquisition}.
     * @return a CompletableFuture (that may complete exceptionally) with the annotation
     * @throws IllegalArgumentException when the provided entity is not an image, dataset, project,
     * screen, plate, or plate acquisition
     */
    public CompletableFuture<AnnotationGroup> getAnnotations(
            long entityId,
            Class<? extends RepositoryEntity> entityClass
    ) {
        logger.debug("Getting OMERO annotations of the {} with ID {}", entityClass, entityId);

        if (!TYPE_TO_URI_LABEL.containsKey(entityClass)) {
            throw new IllegalArgumentException(String.format(
                    "The provided item (%d) is not an image, dataset, project, screen, plate, or plate acquisition.",
                    entityId
            ));
        }

        URI uri;
        try {
            uri = new URI(String.format(READ_ANNOTATION_URL, host, TYPE_TO_URI_LABEL.get(entityClass), entityId));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.getAndConvert(uri, JsonObject.class)
                .thenApply(AnnotationGroup::new);
    }

    /**
     * Attempt to perform a search on the server.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param searchQuery the parameters used in the search
     * @return a CompletableFuture (that may complete exceptionally) with a list of search results, or an empty list if an error occurred
     */
    public CompletableFuture<List<SearchResult>> getSearchResults(SearchQuery searchQuery) {
        logger.debug("Searching with query {}", searchQuery);

        StringBuilder fields = new StringBuilder();
        if (searchQuery.searchOnName()) {
            fields.append("&field=name");
        }
        if (searchQuery.searchOnDescription()) {
            fields.append("&field=description");
        }

        StringBuilder dataTypes = new StringBuilder();
        if (searchQuery.searchForImages()) {
            dataTypes.append("&datatype=images");
        }
        if (searchQuery.searchForDatasets()) {
            dataTypes.append("&datatype=datasets");
        }
        if (searchQuery.searchForProjects()) {
            dataTypes.append("&datatype=projects");
        }
        if (searchQuery.searchForWells()) {
            dataTypes.append("&datatype=wells");
        }
        if (searchQuery.searchForPlates()) {
            dataTypes.append("&datatype=plates");
        }
        if (searchQuery.searchForScreens()) {
            dataTypes.append("&datatype=screens");
        }

        try {
            return requestSender
                    .get(new URI(String.format(SEARCH_URL,
                            host,
                            searchQuery.query(),
                            fields,
                            dataTypes,
                            searchQuery.group().getId(),
                            searchQuery.owner().id(),
                            System.currentTimeMillis()
                    ))).thenApplyAsync(response ->
                            SearchResult.createFromHTMLResponse(response, host)
                    );
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send key-value pairs associated with an image to the OMERO server.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param imageId the id of the image to associate the key value pairs
     * @param namespace the OMERO namespace the key-value pairs should have on the OMERO server
     * @param keyValues the key value pairs to send
     * @param replaceExisting whether to replace values when keys already exist on the OMERO server
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     */
    public CompletableFuture<Void> sendKeyValuePairs(
            long imageId,
            Namespace namespace,
            Map<String, String> keyValues,
            boolean replaceExisting
    ) {
        logger.debug("Sending key-value pairs {} with namespace {} to image with ID {}", keyValues, namespace, imageId);

        URI uri;
        try {
            uri = new URI(String.format(WRITE_KEY_VALUES_URL, host));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return removeAndReturnExistingMapAnnotationsOfNamespace(uri, imageId, namespace).thenApply(existingAnnotations -> {
            List<MapAnnotation.Pair> pairsToSend = new ArrayList<>();
            for (MapAnnotation annotation: existingAnnotations) {
                pairsToSend.addAll(annotation.getPairs());
            }
            for (var entry: keyValues.entrySet()) {
                if (replaceExisting) {
                    pairsToSend.removeAll(pairsToSend.stream().filter(pair -> pair.key().equals(entry.getKey())).toList());
                }
                pairsToSend.add(new MapAnnotation.Pair(entry.getKey(), entry.getValue()));
            }
            return pairsToSend;
        }).thenCompose(pairsToSend -> requestSender.post(
                uri,
                String.format(
                        "image=%d&ns=%s&mapAnnotation=%s",
                        imageId,
                        namespace.name(),
                        URLEncoder.encode(
                                pairsToSend.stream()
                                        .map(pair -> String.format("[\"%s\",\"%s\"]", pair.key(), pair.value()))
                                        .collect(Collectors.joining(",", "[", "]")),
                                StandardCharsets.UTF_8
                        )
                ).getBytes(StandardCharsets.UTF_8),
                String.format("%s/webclient/", host),
                token
        )).thenAccept(rawResponse -> {
            Map<String, List<String>> response = gson.fromJson(rawResponse, new TypeToken<>() {});
            if (response == null || !response.containsKey("annId")) {
                throw new RuntimeException(String.format("The response %s doesn't contain the `annId` key", rawResponse));
            }
        });
    }

    /**
     * Change the name of an image on OMERO.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param imageId the id of the image whose name should be changed
     * @param imageName the new name of the image
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     */
    public CompletableFuture<Void> changeImageName(long imageId, String imageName) {
        logger.debug("Changing image name of image with ID {} to {}", imageId, imageName);

        URI uri;
        try {
            uri = new URI(String.format(WRITE_NAME_URL, host, imageId));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.post(
                uri,
                String.format(
                        "name=%s&",
                        imageName
                ).getBytes(StandardCharsets.UTF_8),
                String.format("%s/webclient/", host),
                token
        ).thenAccept(rawResponse -> {
            Map<String, String> response = gson.fromJson(rawResponse, new TypeToken<>() {});
            if (response == null || !response.containsKey("o_type")) {
                throw new RuntimeException(String.format("The response %s doesn't contain the `o_type` key", rawResponse));
            }
        });
    }

    /**
     * Change the names of the channels of an image on OMERO.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param imageId the id of the image whose channels name should be changed
     * @param channelsName the new names of the channels
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     */
    public CompletableFuture<Void> changeChannelNames(long imageId, List<String> channelsName) {
        logger.debug("Changing channel names of image with ID {} to {}", imageId, channelsName);

        URI uri;
        try {
            uri = new URI(String.format(WRITE_CHANNEL_NAMES_URL, host, imageId));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.post(
                uri,
                String.format(
                        "%ssave=save",
                        IntStream.range(0, channelsName.size())
                                .mapToObj(i -> String.format("channel%d=%s&", i, channelsName.get(i)))
                                .collect(Collectors.joining())
                ).getBytes(StandardCharsets.UTF_8),
                String.format("%s/webclient/", host),
                token
        ).thenAccept(rawResponse -> {
            Map<String, Object> response = gson.fromJson(rawResponse, new TypeToken<>() {});
            if (response == null || !response.containsKey("channelNames")) {
                throw new RuntimeException(String.format("The response %s doesn't contain the `channelNames` key", rawResponse));
            }
        });
    }

    /**
     * Send a file to be attached to a server entity.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param entityId the ID of the entity
     * @param entityClass the class of the entity.
     *                    Must be an {@link Image}, {@link Dataset}, {@link Project},
     *                    {@link Screen}, {@link Plate}, or {@link PlateAcquisition}.
     * @param attachmentName the name of the file to send. A prefix will be added to it to mark
     *                       this file as coming from QuPath
     * @param attachmentContent the content of the file to send
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     * @throws IllegalArgumentException when the provided entity is not an image, dataset, project,
     * screen, plate, or plate acquisition
     */
    public CompletableFuture<Void> sendAttachment(
            long entityId,
            Class<? extends RepositoryEntity> entityClass,
            String attachmentName,
            String attachmentContent
    ) {
        logger.debug("Sending file {} to the {} with ID {}", attachmentName, entityClass, entityId);

        if (!TYPE_TO_URI_LABEL.containsKey(entityClass)) {
            throw new IllegalArgumentException(String.format(
                    "The provided item (%s) is not an image, dataset, project, screen, plate, or plate acquisition.",
                    entityClass
            ));
        }

        URI uri;
        try {
            uri = new URI(String.format(SEND_ATTACHMENT_URL, host));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.post(
                uri,
                QUPATH_FILE_IDENTIFIER + attachmentName,
                attachmentContent,
                String.format("%s/webclient/", host),
                token,
                Map.of(
                        TYPE_TO_URI_LABEL.get(entityClass), String.valueOf(entityId),
                        "index", ""
                )
        ).thenAccept(rawResponse -> {
            Map<String, List<Long>> response = gson.fromJson(rawResponse, new TypeToken<>() {});
            if (response == null || !response.containsKey("fileIds") || response.get("fileIds").isEmpty()) {
                throw new RuntimeException(String.format("The response %s doesn't contain a non-empty `fileIds` value", rawResponse));
            }
        });
    }

    /**
     * Delete all attachments added from QuPath of an OMERO entity.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param entityId the ID of the entity whose attachments should be deleted
     * @param entityClass the class of the entity whose attachments should be deleted.
     *                    Must be an {@link Image}, {@link Dataset}, {@link Project},
     *                    {@link Screen}, {@link Plate}, or {@link PlateAcquisition}.
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     * @throws IllegalArgumentException when the provided entity is not an image, dataset, project,
     * screen, plate, or plate acquisition
     */
    public CompletableFuture<Void> deleteAttachments(long entityId, Class<? extends RepositoryEntity> entityClass) {
        logger.debug("Deleting all attachments added from QuPath to the {} with ID {}", entityClass, entityId);

        return getAnnotations(entityId, entityClass).thenApply(annotationGroup ->
                annotationGroup.getAnnotationsOfClass(FileAnnotation.class).stream()
                        .filter(annotation -> annotation.getFilename().isPresent() && annotation.getFilename().get().startsWith(QUPATH_FILE_IDENTIFIER))
                        .map(Annotation::getId)
                        .toList()
        ).thenAcceptAsync(attachmentIds -> {
            List<String> responses = attachmentIds.stream()
                    .map(annotationId -> URI.create(String.format(DELETE_ATTACHMENT_URL, host, annotationId)))
                    .map(uri -> requestSender.post(uri, "", String.format("%s/webclient/", host), token))
                    .map(CompletableFuture::join)
                    .toList();

            for (String rawResponse: responses) {
                Map<String, String> response = gson.fromJson(rawResponse, new TypeToken<>() {});
                if (response == null || !response.containsKey("bad")) {
                    throw new RuntimeException(String.format("The response %s doesn't contain the `bad` key", rawResponse));
                }
            }
        });
    }

    /**
     * Attempt to retrieve the OMERO image icon.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request or the conversion failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with the icon
     */
    public CompletableFuture<BufferedImage> getImageIcon() {
        logger.debug("Getting OMERO image icon");

        try {
            return requestSender.getImage(new URI(String.format(IMAGE_ICON_URL, host)));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Attempt to retrieve the OMERO screen icon.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request or the conversion failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with the icon
     */
    public CompletableFuture<BufferedImage> getScreenIcon() {
        logger.debug("Getting OMERO screen icon");

        try {
            return requestSender.getImage(new URI(String.format(SCREEN_ICON_URL, host)));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Attempt to retrieve the OMERO plate icon.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request or the conversion failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with the icon
     */
    public CompletableFuture<BufferedImage> getPlateIcon() {
        logger.debug("Getting OMERO plate icon");

        try {
            return requestSender.getImage(new URI(String.format(PLATE_ICON_URL, host)));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Attempt to retrieve the OMERO plate acquisition icon.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request or the conversion failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with the icon
     */
    public CompletableFuture<BufferedImage> getPlateAcquisitionIcon() {
        logger.debug("Getting OMERO plate acquisition icon");

        try {
            return requestSender.getImage(new URI(String.format(PLATE_ACQUISITION_ICON_URL, host)));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<List<MapAnnotation>> removeAndReturnExistingMapAnnotationsOfNamespace(URI uri, long imageId, Namespace namespace) {
        return getAnnotations(imageId, Image.class).thenApply(annotationGroup -> {
            List<MapAnnotation> existingAnnotations = annotationGroup.getAnnotationsOfClass(MapAnnotation.class).stream()
                    .filter(mapAnnotation -> mapAnnotation.getNamespace().isPresent() && mapAnnotation.getNamespace().get().equals(namespace))
                    .toList();

            List<CompletableFuture<String>> requests = existingAnnotations.stream()
                    .map(Annotation::getId)
                    .map(id -> String.format("image=%d&annId=%d&mapAnnotation=\"\"", imageId, id))
                    .map(body -> requestSender.post(
                            uri,
                            body.getBytes(StandardCharsets.UTF_8),
                            String.format("%s/webclient/", host),
                            token
                    ))
                    .toList();

            for (CompletableFuture<String> request: requests) {
                request.join();
            }

            return existingAnnotations;
        });
    }
}
