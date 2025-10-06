package qupath.ext.omero.core.apis.webclient;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.webclient.annotations.Annotation;
import qupath.ext.omero.core.apis.webclient.annotations.AnnotationCreator;
import qupath.ext.omero.core.apis.webclient.annotations.FileAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.MapAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.Pair;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.SimpleServerEntity;
import qupath.ext.omero.core.apis.webclient.search.SearchQuery;
import qupath.ext.omero.core.apis.webclient.search.SearchResult;
import qupath.ext.omero.core.RequestSender;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class WebclientApi implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(WebclientApi.class);
    private static final String PING_URL = "%s/webclient/keepalive_ping/";
    private static final String ITEM_URL = "%s/webclient/?show=%s-%d";
    private static final String LOGOUT_URL = "%s/webclient/logout/";
    private static final String WEBCLIENT_URL = "%s/webclient/";
    private static final Pattern USER_ID_PATTERN = Pattern.compile("WEBCLIENT.USER = \\{'id': (.+?), 'fullName':");
    private static final String PARENTS_OF_IMAGE_URL = "%s/webclient/api/paths_to_object/?image=%d";
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
    private static final Gson gson = new Gson();
    private final URI webServerUri;
    private final RequestSender requestSender;
    private final String token;
    private final URI pingUri;

    /**
     * Creates a web client.
     *
     * @param webServerUri the URL to the OMERO web server to connect to
     * @param requestSender the request sender to use when making requests
     * @param token the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a>
     *              used by this session. This is needed to properly close this API
     * @throws URISyntaxException if the ping URI cannot be created
     */
    public WebclientApi(URI webServerUri, RequestSender requestSender, String token) throws URISyntaxException {
        this.webServerUri = webServerUri;
        this.requestSender = requestSender;
        this.token = token;
        this.pingUri = new URI(String.format(PING_URL, webServerUri));
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
        logger.debug("Sending login out request to {}", webServerUri);

        URI uri = new URI(String.format(LOGOUT_URL, webServerUri));

        requestSender.post(
                uri,
                String.format("csrfmiddlewaretoken=%s", token).getBytes(StandardCharsets.UTF_8),
                uri.toString(),
                token
        ).get();
    }

    @Override
    public String toString() {
        return String.format("Webclient API of %s", webServerUri);
    }

    /**
     * Returns a link of the OMERO.web client pointing to a server entity.
     *
     * @param entity the entity to have a link to
     * @return a URL pointing to the server entity
     */
    public String getEntityUri(SimpleServerEntity entity) {
        return String.format(
                ITEM_URL,
                webServerUri,
                getEntityLabel(entity.entityType()),
                entity.id()
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
            uri = new URI(String.format(WEBCLIENT_URL, webServerUri));
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
     * Attempt to get basic information on all parents of an image.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param imageId the ID of the image whose parents should be retrieved
     * @return the list of parents of the provided image
     */
    public CompletableFuture<List<SimpleServerEntity>> getParentsOfImage(long imageId) {
        logger.debug("Getting all parents of image with ID {}", imageId);

        URI uri;
        try {
            uri = new URI(String.format(PARENTS_OF_IMAGE_URL, webServerUri, imageId));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.getAndConvert(uri, JsonElement.class).thenApply(SimpleServerEntity::createFromJson);
    }

    /**
     * Attempt to retrieve OMERO annotations of an OMERO entity. An OMERO annotation is <b>not</b> similar to a
     * QuPath annotation, it refers to some metadata attached to an entity.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param entity the entity whose annotation should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the annotation group
     */
    public CompletableFuture<List<Annotation>> getAnnotations(SimpleServerEntity entity) {
        logger.debug("Getting OMERO annotations of {}", entity);

        URI uri;
        try {
            uri = new URI(String.format(READ_ANNOTATION_URL, webServerUri, getEntityLabel(entity.entityType()), entity.id()));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.getAndConvert(uri, JsonObject.class).thenApply(AnnotationCreator::createAnnotations);
    }

    /**
     * Attempt to perform a search on the server.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param searchQuery the parameters used in the search
     * @return a CompletableFuture (that may complete exceptionally) with a list of search results
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
                    .get(new URI(String.format(
                            SEARCH_URL,
                            webServerUri,
                            URLEncoder.encode(searchQuery.query(), StandardCharsets.UTF_8),
                            fields,
                            dataTypes,
                            searchQuery.groupId(),
                            searchQuery.experimenterId(),
                            System.currentTimeMillis()
                    ))).thenApply(response ->
                            SearchResult.createFromHTMLResponse(response, webServerUri)
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
        logger.debug(
                "Sending (and {} replacing existing) key-value pairs {} with namespace {} to image with ID {}",
                replaceExisting ? "" : "not",
                keyValues,
                namespace,
                imageId
        );

        URI uri;
        try {
            uri = new URI(String.format(WRITE_KEY_VALUES_URL, webServerUri));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        CompletableFuture<List<Pair>> pairsToSend = replaceExisting ?
                replaceExistingPairsAndReturnPairsNotSent(uri, imageId, namespace, keyValues) :
                CompletableFuture.completedFuture(keyValues.entrySet().stream()
                        .map(entry -> new Pair(entry.getKey(), entry.getValue()))
                        .toList()
                );

        return pairsToSend.thenCompose(pairs -> {
            if (pairs.isEmpty()) {
                logger.debug("No more KVP to send to image with ID {}", imageId);
                return CompletableFuture.completedFuture(null);
            }

            String body = String.format(
                    "image=%d&ns=%s&mapAnnotation=%s",
                    imageId,
                    namespace.name(),
                    URLEncoder.encode(
                            pairs.stream()
                                    .map(pair -> String.format("[\"%s\",\"%s\"]", pair.key(), pair.value()))
                                    .collect(Collectors.joining(",", "[", "]")),
                            StandardCharsets.UTF_8
                    )
            );
            String referer = String.format("%s/webclient/", webServerUri);
            logger.debug(
                    "Creating new KVP {} with namespace {} for image with ID {} by sending body {} with referer {}",
                    pairs,
                    namespace,
                    imageId,
                    body,
                    referer
            );

            return requestSender.post(
                    uri,
                    body.getBytes(StandardCharsets.UTF_8),
                    referer,
                    token
            ).thenAccept(rawResponse -> {
                Map<String, List<String>> response = gson.fromJson(rawResponse, new TypeToken<>() {});
                if (response == null || !response.containsKey("annId")) {
                    throw new RuntimeException(String.format("The response %s doesn't contain the `annId` key", rawResponse));
                }
            });
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
        URI uri;
        try {
            uri = new URI(String.format(WRITE_NAME_URL, webServerUri, imageId));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        String body = String.format(
                "name=%s&",
                imageName
        );
        String referer = String.format("%s/webclient/", webServerUri);
        logger.debug("Changing image name of image with ID {} to {} with body {} and referer {}", imageId, imageName, body, referer);

        return requestSender.post(
                uri,
                body.getBytes(StandardCharsets.UTF_8),
                referer,
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
        URI uri;
        try {
            uri = new URI(String.format(WRITE_CHANNEL_NAMES_URL, webServerUri, imageId));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        String body = String.format(
                "%ssave=save",
                IntStream.range(0, channelsName.size())
                        .mapToObj(i -> String.format("channel%d=%s&", i, channelsName.get(i)))
                        .collect(Collectors.joining())
        );
        String referer = String.format("%s/webclient/", webServerUri);
        logger.debug("Changing channel names of image with ID {} to {} with body {} and referer {}", imageId, channelsName, body, referer);

        return requestSender.post(
                uri,
                body.getBytes(StandardCharsets.UTF_8),
                referer,
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
     * @param entity the entity the file should be attached to
     * @param attachmentName the name of the file to send. A prefix will be added to it to mark this file as coming from QuPath
     * @param attachmentContent the content of the file to send
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     */
    public CompletableFuture<Void> sendAttachment(SimpleServerEntity entity, String attachmentName, String attachmentContent) {
        logger.debug("Sending file {} to {}", attachmentName, entity);

        URI uri;
        try {
            uri = new URI(String.format(SEND_ATTACHMENT_URL, webServerUri));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        String fileName = QUPATH_FILE_IDENTIFIER + attachmentName;
        String referer = String.format("%s/webclient/", webServerUri);
        Map<String, String> parameters = Map.of(
                getEntityLabel(entity.entityType()), String.valueOf(entity.id()),
                "index", ""
        );
        logger.debug("Sending file {} with parameters {} and referer {} with the following content: {}", fileName, parameters, referer, attachmentContent);

        return requestSender.post(
                uri,
                fileName,
                attachmentContent,
                referer,
                token,
                parameters
        ).thenAccept(rawResponse -> {
            Map<String, List<Long>> response = gson.fromJson(rawResponse, new TypeToken<>() {});
            if (response == null || !response.containsKey("fileIds") || response.get("fileIds").isEmpty()) {
                throw new RuntimeException(String.format("The response %s doesn't contain a non-empty `fileIds` value", rawResponse));
            }
        });
    }

    /**
     * Delete attachments belonging to a list of users added from QuPath of an OMERO entity.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param entity the entity whose attachments should be deleted
     * @param experimenterFullNames the full names (first name + last name) of the experimenters that should own the attachments to remove
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     */
    public CompletableFuture<Void> deleteAttachments(SimpleServerEntity entity, List<String> experimenterFullNames) {
        logger.debug("Deleting all attachments added from QuPath to {} belonging to experimenters {}", entity, experimenterFullNames);

        return getAnnotations(entity).thenApply(annotations -> {
            List<FileAnnotation> annotationsToDelete = annotations.stream()
                    .filter(FileAnnotation.class::isInstance)
                    .map(FileAnnotation.class::cast)
                    .filter(annotation -> experimenterFullNames.contains(annotation.getOwnerName().orElse("")))
                    .filter(annotation -> annotation.getFilename().startsWith(QUPATH_FILE_IDENTIFIER))
                    .toList();

            logger.debug("Retrieved annotations {} filtered to {}. Deleting these annotations", annotations, annotationsToDelete);
            return annotationsToDelete;
        }).thenAccept(annotationsToDelete -> {
            List<String> responses = annotationsToDelete.stream()
                    .map(Annotation::getId)
                    .map(annotationId -> URI.create(String.format(DELETE_ATTACHMENT_URL, webServerUri, annotationId)))
                    .map(uri -> requestSender.post(uri, "", String.format("%s/webclient/", webServerUri), token))
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
            return requestSender.getImage(new URI(String.format(IMAGE_ICON_URL, webServerUri)));
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
            return requestSender.getImage(new URI(String.format(SCREEN_ICON_URL, webServerUri)));
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
            return requestSender.getImage(new URI(String.format(PLATE_ICON_URL, webServerUri)));
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
            return requestSender.getImage(new URI(String.format(PLATE_ACQUISITION_ICON_URL, webServerUri)));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static String getEntityLabel(SimpleServerEntity.EntityType entityType) {
        return switch (entityType) {
            case SCREEN -> "screen";
            case PLATE -> "plate";
            case PLATE_ACQUISITION -> "run";
            case WELL -> "well";
            case PROJECT -> "project";
            case DATASET -> "dataset";
            case IMAGE -> "image";
        };
    }

    private CompletableFuture<List<Pair>> replaceExistingPairsAndReturnPairsNotSent(
            URI uri,
            long imageId,
            Namespace namespace,
            Map<String, String> keyValues
    ) {
        logger.debug("Replacing KVP of namespace {} on image with ID {} contained in {}", namespace, imageId, keyValues);

        return getAnnotations(new SimpleServerEntity(SimpleServerEntity.EntityType.IMAGE, imageId)).thenApply(annotations -> {
            List<MapAnnotation> existingAnnotations = annotations.stream()
                    .filter(annotation -> annotation.getNamespace().isPresent() && annotation.getNamespace().get().equals(namespace))
                    .filter(MapAnnotation.class::isInstance)
                    .map(MapAnnotation.class::cast)
                    .toList();
            logger.debug(
                    "Got annotations {} reduced to map annotations {} for image with ID {}. Replacing all pairs with keys in {}",
                    annotations,
                    existingAnnotations,
                    imageId,
                    keyValues
            );

            Set<String> keysSent = new HashSet<>();
            for (MapAnnotation mapAnnotation : existingAnnotations) {
                List<Pair> pairsToSend = new ArrayList<>();
                boolean pairReplaced = false;

                for (Pair pair : mapAnnotation.getPairs()) {
                    if (keyValues.containsKey(pair.key())) {
                        pairReplaced = true;
                        pairsToSend.add(new Pair(pair.key(), keyValues.get(pair.key())));

                        keysSent.add(pair.key());
                    } else {
                        pairsToSend.add(pair);
                    }
                }

                if (pairReplaced) {
                    logger.debug("Keys of {} found in {}. Resending this annotation with the following pairs: {}", keyValues, mapAnnotation, pairsToSend);

                    requestSender.post(
                            uri,
                            String.format(
                                    "image=%d&annId=%d&mapAnnotation=%s",
                                    imageId,
                                    mapAnnotation.getId(),
                                    URLEncoder.encode(
                                            pairsToSend.stream()
                                                    .map(pair -> String.format("[\"%s\",\"%s\"]", pair.key(), pair.value()))
                                                    .collect(Collectors.joining(",", "[", "]")),
                                            StandardCharsets.UTF_8
                                    )
                            ).getBytes(StandardCharsets.UTF_8),
                            String.format("%s/webclient/", webServerUri),
                            token
                    ).join();
                } else {
                    logger.debug("No keys of {} found in {}. Skipping this annotation", keyValues, mapAnnotation);
                }
            }

            return keyValues.entrySet().stream()
                    .filter(entry -> !keysSent.contains(entry.getKey()))
                    .map(entry -> new Pair(entry.getKey(), entry.getValue()))
                    .toList();
        });
    }
}
