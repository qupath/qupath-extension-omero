package qupath.ext.omero.core.apis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import qupath.ext.omero.core.WebUtilities;
import qupath.ext.omero.core.RequestSender;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>API to communicate with a OMERO.web server.</p>
 * <p>
 *     This API is mainly used to keep a connection alive, log out, perform a search
 *     and get OMERO annotations.
 * </p>
 * <p>An instance of this class must be {@link #close() closed} once no longer used.</p>
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
    private final URI host;
    private final URI pingUri;
    private final String token;

    /**
     * Creates a web client.
     *
     * @param host  the base server URI (e.g. <a href="https://idr.openmicroscopy.org">https://idr.openmicroscopy.org</a>)
     * @param token  the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a>
     *               used by this session. This is needed to properly close this API.
     */
    public WebclientApi(URI host, String token) {
        this.host = host;
        this.token = token;

        pingUri = WebUtilities.createURI(String.format(PING_URL, host)).orElse(null);
    }

    @Override
    public void close() {
        if (token != null) {
            WebUtilities.createURI(String.format(LOGOUT_URL, host)).ifPresent(value -> RequestSender.post(
                    value,
                    Map.of("csrfmiddlewaretoken", token),
                    value.toString(),
                    token
            ));
        }
    }

    @Override
    public String toString() {
        return String.format("Webclient API of %s", host);
    }

    /**
     * Returns a link of the OMERO.web client pointing to a server entity.
     *
     * @param entity  the entity to have a link to.
     *                Must be an {@link Image}, {@link Dataset}, {@link Project},
     *                {@link Screen}, {@link Plate} or {@link PlateAcquisition}
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

        return String.format(ITEM_URL,
                host,
                TYPE_TO_URI_LABEL.get(entity.getClass()),
                entity.getId()
        );
    }

    /**
     * <p>
     *     Attempt to send a ping to the server. This is needed to keep the connection alive between the client
     *     and the server.
     * </p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture indicating the success of the operation
     */
    public CompletableFuture<Boolean> ping() {
        if (pingUri == null) {
            return CompletableFuture.completedFuture(false);
        } else {
            return RequestSender.isLinkReachableWithGet(pingUri);
        }
    }

    /**
     * <p>Attempt to get the image IDs of all orphaned images of the server.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with a list containing the ID of all orphaned images,
     * or an empty list if an error occurred
     */
    public CompletableFuture<List<Long>> getOrphanedImagesIds() {
        var uri = WebUtilities.createURI(String.format(ORPHANED_IMAGES_URL, host));

        if (uri.isPresent()) {
            return RequestSender.getAndConvertToJsonList(uri.get(), "images").thenApply(elements ->
                    elements.stream()
                            .map(jsonElement -> {
                                try {
                                    return Long.parseLong(jsonElement.getAsJsonObject().get("id").toString());
                                } catch (Exception e) {
                                    logger.error("Could not parse " + jsonElement, e);
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .toList()
            );
        } else {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * <p>
     *     Attempt to get the ID of the public user of the server.
     *     This only works if there is no active authenticated connection with the server.
     * </p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the public user ID, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<Long>> getPublicUserId() {
        var uri = WebUtilities.createURI(String.format(WEBCLIENT_URL, host));

        if (uri.isPresent()) {
            return RequestSender.get(uri.get()).thenApply(content -> {
                if (content.isPresent()) {
                    Matcher matcher = USER_ID_PATTERN.matcher(content.get());

                    if (matcher.find()) {
                        String id = matcher.group(1);
                        try {
                            return Optional.of(Long.parseLong(id));
                        } catch (NumberFormatException e) {
                            logger.error(String.format("Could not convert %s to long", id));
                        }
                    }
                }
                return Optional.empty();
            });
        } else {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /**
     * <p>
     *     Attempt to retrieve OMERO annotations of an OMERO entity .
     *     An OMERO annotation is <b>not</b> similar to a QuPath annotation, it refers to some metadata
     *     attached to an entity.
     * </p>
     * <p>This function is asynchronous.</p>
     *
     * @param entityId  the ID of the entity
     * @param entityClass  the class of the entity whose annotation should be retrieved.
     *                Must be an {@link Image}, {@link Dataset}, {@link Project},
     *                {@link Screen}, {@link Plate}, or {@link PlateAcquisition}.
     * @return a CompletableFuture with the annotation, or an empty Optional if an error occurred
     * @throws IllegalArgumentException when the provided entity is not an image, dataset, project,
     * screen, plate, or plate acquisition
     */
    public CompletableFuture<Optional<AnnotationGroup>> getAnnotations(
            long entityId,
            Class<? extends RepositoryEntity> entityClass
    ) {
        if (!TYPE_TO_URI_LABEL.containsKey(entityClass)) {
            throw new IllegalArgumentException(String.format(
                    "The provided item (%d) is not an image, dataset, project, screen, plate, or plate acquisition.",
                    entityId
            ));
        }

        var uri = WebUtilities.createURI(String.format(
                READ_ANNOTATION_URL,
                host,
                TYPE_TO_URI_LABEL.get(entityClass),
                entityId
        ));

        if (uri.isPresent()) {
            return RequestSender.getAndConvert(uri.get(), JsonObject.class)
                    .thenApply(json -> json.map(AnnotationGroup::new));
        } else {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /**
     * <p>Attempt to perform a search on the server.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param searchQuery  the parameters used in the search
     * @return a CompletableFuture with a list of search results, or an empty list if an error occurred
     */
    public CompletableFuture<List<SearchResult>> getSearchResults(SearchQuery searchQuery) {
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

        var uri = WebUtilities.createURI(String.format(SEARCH_URL,
                host,
                searchQuery.query(),
                fields,
                dataTypes,
                searchQuery.group().getId(),
                searchQuery.owner().id(),
                System.currentTimeMillis()
        ));
        if (uri.isPresent()) {
            return RequestSender.get(uri.get()).thenApply(response ->
                    response.map(s -> SearchResult.createFromHTMLResponse(s, host)).orElseGet(List::of)
            );
        } else {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * <p>
     *     Send key value pairs associated with an image to the OMERO server.
     * </p>
     * <p>This function is asynchronous.</p>
     *
     * @param imageId  the id of the image to associate the key value pairs
     * @param keyValues  the key value pairs to send
     * @param replaceExisting  whether to replace values when keys already exist on the OMERO server
     * @param deleteExisting  whether to delete all existing key value pairs on the OMERO server
     * @return a CompletableFuture indicating the success of the operation
     */
    public CompletableFuture<Boolean> sendKeyValuePairs(
            long imageId,
            Map<String, String> keyValues,
            boolean replaceExisting,
            boolean deleteExisting
    ) {
        var uri = WebUtilities.createURI(String.format(
                WRITE_KEY_VALUES_URL,
                host
        ));

        if (uri.isPresent()) {
            return removeAndReturnExistingMapAnnotations(uri.get(), imageId).thenCompose(existingAnnotations -> {
                Map<String, String> keyValuesToSend;
                if (deleteExisting) {
                    keyValuesToSend = keyValues;
                } else {
                    keyValuesToSend = Stream.of(keyValues, MapAnnotation.getCombinedValues(existingAnnotations))
                            .flatMap(map -> map.entrySet().stream())
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (value1, value2) -> replaceExisting ? value1 : value2
                            ));
                }

                return RequestSender.post(
                        uri.get(),
                        String.format(
                                "image=%d&mapAnnotation=%s",
                                imageId,
                                URLEncoder.encode(
                                        keyValuesToSend.keySet().stream()
                                                .map(key -> String.format("[\"%s\",\"%s\"]", key, keyValuesToSend.get(key)))
                                                .collect(Collectors.joining(",", "[", "]")),
                                        StandardCharsets.UTF_8
                                )
                        ).getBytes(StandardCharsets.UTF_8),
                        String.format("%s/webclient/", host),
                        token
                ).thenApply(rawResponse -> {
                    if (rawResponse.isPresent()) {
                        Gson gson = new Gson();
                        try {
                            Map<String, List<String>> response = gson.fromJson(rawResponse.get(), new TypeToken<>() {});
                            return response != null && response.containsKey("annId");
                        } catch (JsonSyntaxException e) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                });
            });
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * <p>
     *     Change the name of an image on OMERO.
     * </p>
     * <p>This function is asynchronous.</p>
     *
     * @param imageId  the id of the image whose name should be changed
     * @param imageName  the new name of the image
     * @return a CompletableFuture indicating the success of the operation
     */
    public CompletableFuture<Boolean> changeImageName(long imageId, String imageName) {
        var uri = WebUtilities.createURI(String.format(
                WRITE_NAME_URL,
                host,
                imageId
        ));

        if (uri.isPresent()) {
            return RequestSender.post(
                    uri.get(),
                    String.format(
                            "name=%s&",
                            imageName
                    ).getBytes(StandardCharsets.UTF_8),
                    String.format("%s/webclient/", host),
                    token
            ).thenApply(rawResponse -> {
                if (rawResponse.isPresent()) {
                    Gson gson = new Gson();
                    try {
                        Map<String, String> response = gson.fromJson(rawResponse.get(), new TypeToken<>() {});
                        return response != null && response.containsKey("o_type");
                    } catch (JsonSyntaxException e) {
                        return false;
                    }
                } else {
                    return false;
                }
            });
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * <p>
     *     Change the names of the channels of an image on OMERO.
     * </p>
     * <p>This function is asynchronous.</p>
     *
     * @param imageId  the id of the image whose channels name should be changed
     * @param channelsName  the new names of the channels
     * @return a CompletableFuture indicating the success of the operation
     */
    public CompletableFuture<Boolean> changeChannelNames(long imageId, List<String> channelsName) {
        var uri = WebUtilities.createURI(String.format(
                WRITE_CHANNEL_NAMES_URL,
                host,
                imageId
        ));

        StringBuilder body = new StringBuilder();
        for (int i=0; i<channelsName.size(); i++) {
            body.append(String.format("channel%d=%s&", i, channelsName.get(i)));
        }

        if (uri.isPresent()) {
            return RequestSender.post(
                    uri.get(),
                    String.format(
                            "%ssave=save",
                            body
                    ).getBytes(StandardCharsets.UTF_8),
                    String.format("%s/webclient/", host),
                    token
            ).thenApply(rawResponse -> {
                if (rawResponse.isPresent()) {
                    Gson gson = new Gson();
                    try {
                        Map<String, Object> response = gson.fromJson(rawResponse.get(), new TypeToken<>() {});
                        return response != null && response.containsKey("channelNames");
                    } catch (JsonSyntaxException e) {
                        return false;
                    }
                } else {
                    return false;
                }
            });
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * <p>Send a file to be attached to a server entity.</p>
     *
     * @param entityId  the ID of the entity
     * @param entityClass  the class of the entity.
     *                     Must be an {@link Image}, {@link Dataset}, {@link Project},
     *                     {@link Screen}, {@link Plate}, or {@link PlateAcquisition}.
     * @param attachmentName  the name of the file to send
     * @param attachmentContent  the content of the file to send
     * @return a CompletableFuture indicating the success of the operation
     * @throws IllegalArgumentException when the provided entity is not an image, dataset, project,
     * screen, plate, or plate acquisition
     */
    public CompletableFuture<Boolean> sendAttachment(
            long entityId,
            Class<? extends RepositoryEntity> entityClass,
            String attachmentName,
            String attachmentContent
    ) {
        if (!TYPE_TO_URI_LABEL.containsKey(entityClass)) {
            throw new IllegalArgumentException(String.format(
                    "The provided item (%s) is not an image, dataset, project, screen, plate, or plate acquisition.",
                    entityClass
            ));
        }

        var uri = WebUtilities.createURI(String.format(
                SEND_ATTACHMENT_URL,
                host
        ));

        if (uri.isPresent()) {
            return RequestSender.post(
                    uri.get(),
                    attachmentName,
                    attachmentContent,
                    String.format("%s/webclient/", host),
                    token,
                    Map.of(
                            TYPE_TO_URI_LABEL.get(entityClass), String.valueOf(entityId),
                            "index", ""
                    )
            ).thenApply(rawResponse -> {
                if (rawResponse.isPresent()) {
                    Gson gson = new Gson();
                    try {
                        Map<String, List<Long>> response = gson.fromJson(rawResponse.get(), new TypeToken<>() {});
                        return response != null && response.containsKey("fileIds") && !response.get("fileIds").isEmpty();
                    } catch (JsonSyntaxException e) {
                        return false;
                    }
                } else {
                    return false;
                }
            });
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * <p>Delete all attachments of an OMERO entity.</p>
     *
     * @param entityId  the ID of the entity whose attachments should be deleted
     * @param entityClass  the class of the entity whose attachments should be deleted.
     *                     Must be an {@link Image}, {@link Dataset}, {@link Project},
     *                     {@link Screen}, {@link Plate}, or {@link PlateAcquisition}.
     * @return a CompletableFuture indicating the success of the operation
     * @throws IllegalArgumentException when the provided entity is not an image, dataset, project,
     * screen, plate, or plate acquisition
     */
    public CompletableFuture<Boolean> deleteAttachments(long entityId, Class<? extends RepositoryEntity> entityClass) {
        return getAnnotations(entityId, entityClass).thenApply(annotationGroup ->
                annotationGroup
                        .map(group -> group.getAnnotationsOfClass(FileAnnotation.class).stream()
                                .map(Annotation::getId)
                                .toList()
                        )
                        .orElseGet(List::of)
        ).thenApplyAsync(attachmentIds -> {
            List<URI> uris = attachmentIds.stream()
                    .map(annotationId -> WebUtilities.createURI(String.format(DELETE_ATTACHMENT_URL, host, annotationId)))
                    .flatMap(Optional::stream)
                    .toList();

            if (uris.size() == attachmentIds.size()) {
                List<String> responses = uris.stream()
                        .map(uri -> RequestSender.post(uri, "", String.format("%s/webclient/", host), token))
                        .map(CompletableFuture::join)
                        .flatMap(Optional::stream)
                        .filter(rawResponse -> {
                            Gson gson = new Gson();
                            try {
                                Map<String, String> response = gson.fromJson(rawResponse, new TypeToken<>() {});
                                return response != null && response.containsKey("bad");
                            } catch (JsonSyntaxException e) {
                                return false;
                            }
                        })
                        .toList();

                return responses.size() == attachmentIds.size();
            } else {
                return false;
            }
        });
    }

    /**
     * <p>Attempt to retrieve the OMERO image icon.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the icon, of an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> getImageIcon() {
        return ApiUtilities.getImage(String.format(IMAGE_ICON_URL, host));
    }

    /**
     * <p>Attempt to retrieve the OMERO screen icon.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the icon, of an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> getScreenIcon() {
        return ApiUtilities.getImage(String.format(SCREEN_ICON_URL, host));
    }

    /**
     * <p>Attempt to retrieve the OMERO plate icon.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the icon, of an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> getPlateIcon() {
        return ApiUtilities.getImage(String.format(PLATE_ICON_URL, host));
    }

    /**
     * <p>Attempt to retrieve the OMERO plate acquisition icon.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the icon, of an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> getPlateAcquisitionIcon() {
        return ApiUtilities.getImage(String.format(PLATE_ACQUISITION_ICON_URL, host));
    }

    private CompletableFuture<List<MapAnnotation>> removeAndReturnExistingMapAnnotations(URI uri, long imageId) {
        return getAnnotations(imageId, Image.class).thenApplyAsync(annotationGroupResponse -> {
            List<MapAnnotation> existingAnnotations = annotationGroupResponse
                    .map(annotationGroup -> annotationGroup.getAnnotationsOfClass(MapAnnotation.class))
                    .orElse(List.of());

            existingAnnotations.stream()
                    .map(Annotation::getId)
                    .map(id -> String.format("image=%d&annId=%d&mapAnnotation=\"\"", imageId, id))
                    .map(body -> RequestSender.post(
                            uri,
                            body.getBytes(StandardCharsets.UTF_8),
                            String.format("%s/webclient/", host),
                            token
                    ))
                    .forEach(CompletableFuture::join);

            return existingAnnotations;
        });
    }
}
