package qupath.ext.omero.core.apis;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.entities.Namespace;
import qupath.ext.omero.core.entities.annotations.AnnotationGroup;
import qupath.ext.omero.core.entities.image.ChannelSettings;
import qupath.ext.omero.core.entities.image.ImageSettings;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.repositoryentities.OrphanedFolder;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Well;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.search.SearchQuery;
import qupath.ext.omero.core.entities.search.SearchResult;
import qupath.ext.omero.core.entities.shapes.Shape;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class provides functions to perform operations with an OMERO server.
 * <p>
 * As different APIs are used to perform the operations, this class only redirects
 * each web request to the appropriate API contained in this package.
 * <p>
 * An instance of this class must be {@link #close() closed} once no longer used.
 */
public class ApisHandler implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ApisHandler.class);
    private static final int THUMBNAIL_SIZE = 256;
    private static final int THUMBNAIL_CACHE_SIZE = 1000;
    private static final int METADATA_CACHE_SIZE = 50;
    private static final Map<String, PixelType> PIXEL_TYPE_MAP = Map.of(
            "uint8", PixelType.UINT8,
            "int8", PixelType.INT8,
            "uint16", PixelType.UINT16,
            "int16", PixelType.INT16,
            "int32", PixelType.INT32,
            "uint32", PixelType.UINT32,
            "float", PixelType.FLOAT32,
            "double", PixelType.FLOAT64
    );
    private static final Pattern webclientImagePattern = Pattern.compile("/webclient/\\?show=image-(\\d+)");
    private static final Pattern webclientImagePatternAlternate = Pattern.compile("/webclient/img_detail/(\\d+)");
    private static final Pattern webgatewayImagePattern = Pattern.compile("/webgateway/img_detail/(\\d+)");
    private static final Pattern iviewerImagePattern = Pattern.compile("/iviewer/\\?images=(\\d+)");
    private static final Pattern datasetPattern = Pattern.compile("/webclient/\\?show=dataset-(\\d+)");
    private static final Pattern projectPattern = Pattern.compile("/webclient/\\?show=project-(\\d+)");
    private static final List<Pattern> allPatterns = List.of(
            webclientImagePattern,
            webclientImagePatternAlternate,
            webgatewayImagePattern,
            iviewerImagePattern,
            datasetPattern,
            projectPattern
    );
    private static final List<Pattern> imagePatterns = List.of(
            webclientImagePattern,
            webclientImagePatternAlternate,
            webgatewayImagePattern,
            iviewerImagePattern
    );
    private final BooleanProperty areOrphanedImagesLoading = new SimpleBooleanProperty(false);
    private final Cache<Long, CompletableFuture<Image>> imagesCache = CacheBuilder.newBuilder()
            .build();
    private final Cache<Class<? extends RepositoryEntity>, CompletableFuture<BufferedImage>> omeroIconsCache = CacheBuilder.newBuilder()
            .build();
    private final Cache<IdSizeWrapper, CompletableFuture<BufferedImage>> thumbnailsCache = CacheBuilder.newBuilder()
            .maximumSize(THUMBNAIL_CACHE_SIZE)
            .build();
    private final Cache<Long, CompletableFuture<ImageServerMetadata>> metadataCache = CacheBuilder.newBuilder()
            .maximumSize(METADATA_CACHE_SIZE)
            .build();
    private final URI webServerUri;
    private final Credentials credentials;
    private final RequestSender requestSender = new RequestSender();
    private final JsonApi jsonApi;
    private final WebclientApi webclientApi;
    private final WebGatewayApi webGatewayApi;
    private final IViewerApi iViewerApi;
    private CompletableFuture<List<Long>> orphanedImagesIds = null;
    private record IdSizeWrapper(long id, int size) {}

    /**
     * Create an APIs handler. This will send a few requests to get basic information on the server and
     * authenticate if necessary, so it can take a few seconds. However, this operation is cancellable.
     *
     * @param webServerUri the URL to the OMERO web server to connect to
     * @param credentials the credentials to use for the authentication
     * @throws URISyntaxException if a link to the server cannot be created
     * @throws ExecutionException if a request to the server fails or if a response does not contain expected elements.
     * This can happen if the server is unreachable or if the authentication fails for example
     * @throws InterruptedException if the running thread is interrupted
     * @throws IllegalArgumentException if the server doesn't return all necessary information on it
     */
    public ApisHandler(URI webServerUri, Credentials credentials) throws URISyntaxException, ExecutionException, InterruptedException {
        this.webServerUri = webServerUri;
        this.credentials = credentials;
        this.jsonApi = new JsonApi(webServerUri, requestSender, credentials);
        this.webclientApi = new WebclientApi(webServerUri, requestSender, jsonApi.getToken());
        this.webGatewayApi = new WebGatewayApi(webServerUri, requestSender, jsonApi.getToken());
        this.iViewerApi = new IViewerApi(webServerUri, requestSender);
    }

    /**
     * Close the connection.
     * This may take a moment as an HTTP request is made.
     *
     * @throws Exception when an error occurs when closing the connection
     */
    @Override
    public void close() throws Exception {
        webclientApi.close();
        requestSender.close();
    }

    @Override
    public String toString() {
        return String.format("APIs handler of %s", webServerUri);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ApisHandler that = (ApisHandler) object;
        return Objects.equals(webServerUri, that.webServerUri) && Objects.equals(credentials, that.credentials);
    }

    @Override
    public int hashCode() {
        return Objects.hash(webServerUri, credentials);
    }

    /**
     * Convert a pixel type returned by OMERO to a QuPath {@link PixelType}
     *
     * @param pixelType the OMERO pixel type
     * @return the QuPath pixel type, or an empty Optional if the OMERO pixel type was not recognized
     */
    public static Optional<PixelType> getPixelType(String pixelType) {
        return Optional.ofNullable(PIXEL_TYPE_MAP.get(pixelType));
    }

    /**
     * Get the URI of the OMERO <b>web server</b> (e.g. <a href="https://idr.openmicroscopy.org">https://idr.openmicroscopy.org</a>).
     * This may be different from the OMERO <b>server</b> URI.
     * See {@link #getServerURI()} for more information.
     *
     * @return the URI of the web server
     */
    public URI getWebServerURI() {
        return webServerUri;
    }

    /**
     * @return the credentials used for authenticating to this server
     */
    public Credentials getCredentials() {
        return credentials;
    }

    /**
     * See {@link JsonApi#getServerUri()}.
     */
    public String getServerURI() {
        return jsonApi.getServerUri();
    }

    /**
     * See {@link JsonApi#getServerPort()}.
     */
    public int getServerPort() {
        return jsonApi.getServerPort();
    }

    /**
     * @return the ID of the connected user, or the ID of the public user of
     * the server if no authentication was performed
     */
    public CompletableFuture<Long> getUserId() {
        return jsonApi.getUserId()
                .map(CompletableFuture::completedFuture)
                .orElseGet(webclientApi::getPublicUserId);
    }

    /**
     * See {@link JsonApi#getDefaultGroup()}.
     */
    public Optional<Group> getDefaultGroup() {
        return jsonApi.getDefaultGroup();
    }

    /**
     * See {@link JsonApi#getSessionUuid()}.
     */
    public Optional<String> getSessionUuid() {
        return jsonApi.getSessionUuid();
    }

    /**
     * See {@link JsonApi#isAdmin()}.
     */
    public Optional<Boolean> isAdmin() {
        return jsonApi.isAdmin();
    }

    /**
     * See {@link JsonApi#isConnectedUserOwnerOfGroup(long)}.
     */
    public boolean isConnectedUserOwnerOfGroup(long groupId) {
        return jsonApi.isConnectedUserOwnerOfGroup(groupId);
    }

    /**
     * Performs a request to the specified URI to determine if it is reachable
     * and returns a 200 status code. The request will follow redirections and
     * use session cookies.
     *
     * @param uri the link of the request
     * @param requestType the type of request to send
     * @return a void CompletableFuture (that completes exceptionally if the link is not reachable)
     */
    public CompletableFuture<Void> isLinkReachable(URI uri, RequestSender.RequestType requestType) {
        return requestSender.isLinkReachable(uri, requestType, true, true);
    }

    /**
     * Parse the OMERO entity ID from a URI.
     *
     * @param uri the URI that is supposed to contain the ID. It can be URL encoded
     * @return the entity ID, or an empty Optional if it was not found
     */
    public static Optional<Long> parseEntityId(URI uri) {
        logger.debug("Finding entity ID in {}...", uri);

        for (Pattern pattern : allPatterns) {
            Matcher matcher = pattern.matcher(URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8));

            if (matcher.find()) {
                String idValue = matcher.group(1);
                try {
                    long id = Long.parseLong(idValue);
                    logger.debug("Found ID {} in {} with pattern {}", id, uri, pattern);

                    return Optional.of(id);
                } catch (NumberFormatException e) {
                    logger.debug("Found entity ID {} in {} with pattern {} but it is not an integer", idValue, uri, pattern, e);
                }
            } else {
                logger.debug("No ID found with pattern {} for uri {}", pattern, uri);
            }
        }

        logger.debug("No ID was found in {}", uri);
        return Optional.empty();
    }

    /**
     * Attempt to retrieve the image URIs indicated by the provided entity URI.
     * <ul>
     *     <li>If the entity is a dataset, the URIs of the children of this dataset (which are images) are returned.</li>
     *     <li>If the entity is a project, the URIs of each child of the datasets of this project are returned.</li>
     *     <li>If the entity is an image, the input URI is returned.</li>
     *     <li>Else, an error is returned.</li>
     * </ul>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request or the conversion failed for example).
     *
     * @param entityUri the URI of the entity whose images should be retrieved. It can be URL encoded
     * @return a CompletableFuture (that may complete exceptionally) with the list described above
     */
    public CompletableFuture<List<URI>> getImagesURIFromEntityURI(URI entityUri) {
        logger.debug("Finding image URIs indicated by {}...", entityUri);

        String entityUrl = URLDecoder.decode(entityUri.toString(), StandardCharsets.UTF_8);

        if (projectPattern.matcher(entityUrl).find()) {
            logger.debug("{} refers to a project", entityUri);

            return parseEntityId(entityUri)
                    .map(this::getImagesURIOfProject)
                    .orElse(CompletableFuture.failedFuture(new IllegalArgumentException(
                            String.format("The provided URI %s was detected as a project but no ID was found", entityUrl)
                    )));
        } else if (datasetPattern.matcher(entityUrl).find()) {
            logger.debug("{} refers to a dataset", entityUri);

            return parseEntityId(entityUri)
                    .map(this::getImagesURIOfDataset)
                    .orElse(CompletableFuture.failedFuture(new IllegalArgumentException(
                            String.format("The provided URI %s was detected as a dataset but no ID was found", entityUrl)
                    )));
        } else if (imagePatterns.stream().anyMatch(pattern -> pattern.matcher(entityUrl).find())) {
            logger.debug("{} refers to an image", entityUri);

            return CompletableFuture.completedFuture(List.of(entityUri));
        } else {
            logger.debug("{} doesn't refer to a project, dataset, or image", entityUri);

            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    String.format("The provided URI %s does not represent a project, dataset, or image", entityUrl)
            ));
        }
    }

    /**
     * See {@link RequestSender#getImage(URI)}.
     */
    public CompletableFuture<BufferedImage> getImage(URI uri) {
        return requestSender.getImage(uri);
    }

    /**
     * See {@link WebclientApi#getEntityURI(ServerEntity)}.
     */
    public String getItemURI(ServerEntity entity) {
        return webclientApi.getEntityURI(entity);
    }

    /**
     * See {@link JsonApi#getNumberOfEntitiesLoading}.
     */
    public ReadOnlyIntegerProperty getNumberOfEntitiesLoading() {
        return jsonApi.getNumberOfEntitiesLoading();
    }

    /**
     * See {@link WebGatewayApi#getNumberOfThumbnailsLoading()}.
     */
    public ReadOnlyIntegerProperty getNumberOfThumbnailsLoading() {
        return webGatewayApi.getNumberOfThumbnailsLoading();
    }

    /**
     * See {@link WebclientApi#ping()}.
     */
    public CompletableFuture<Void> ping() {
        return webclientApi.ping();
    }

    /**
     * See {@link WebclientApi#getOrphanedImagesIds()}.
     */
    public synchronized CompletableFuture<List<Long>> getOrphanedImagesIds() {
        logger.debug("Getting orphaned image IDs");

        if (orphanedImagesIds == null) {
            logger.debug("Orphaned image IDs not cached. Retrieving them");
            orphanedImagesIds = webclientApi.getOrphanedImagesIds();
        }

        return orphanedImagesIds;
    }

    /**
     * See {@link JsonApi#getGroups(long)}.
     */
    public CompletableFuture<List<Group>> getGroups(long userId) {
        return jsonApi.getGroups(userId);
    }

    /**
     * Same as {@link #getGroups(long)}, but to get all groups of the server
     */
    public CompletableFuture<List<Group>> getGroups() {
        return jsonApi.getGroups(-1);
    }

    /**
     * See {@link JsonApi#getProjects()}.
     */
    public CompletableFuture<List<Project>> getProjects() {
        return jsonApi.getProjects();
    }

    /**
     * See {@link JsonApi#getOrphanedDatasets()}.
     */
    public CompletableFuture<List<Dataset>> getOrphanedDatasets() {
        return jsonApi.getOrphanedDatasets();
    }

    /**
     * See {@link JsonApi#getDatasets(long)}.
     */
    public CompletableFuture<List<Dataset>> getDatasets(long projectId) {
        return jsonApi.getDatasets(projectId);
    }

    /**
     * Attempt to retrieve the parent dataset of an image.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if there is no parent dataset for example).
     *
     * @param imageId the ID of the image whose parent dataset should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the parent dataset of the provided image
     */
    public CompletableFuture<Dataset> getDatasetOwningImage(long imageId) {
        logger.debug("Getting dataset owning image with ID {}", imageId);

        return getImage(imageId)
                .thenApply(image -> image.getDatasetsUrl().orElseThrow())
                .thenCompose(datasetUrl -> {
                    logger.debug("Got dataset URL {}. Sending request to it to get dataset", datasetUrl);
                    return requestSender.getPaginated(URI.create(datasetUrl));
                })
                .thenApply(jsonElements -> jsonElements.stream()
                        .map(jsonElement -> ServerEntity.createFromJsonElement(jsonElement, webServerUri))
                        .toList()
                )
                .thenApply(serverEntities ->
                        serverEntities.stream()
                                .filter(serverEntity -> serverEntity instanceof Dataset)
                                .map(serverEntity -> (Dataset) serverEntity)
                                .findAny()
                                .orElseThrow()
                );
    }

    /**
     * See {@link JsonApi#getImages(long)}.
     */
    public CompletableFuture<List<Image>> getImages(long datasetId) {
        return jsonApi.getImages(datasetId);
    }

    /**
     * See {@link JsonApi#getImage(long)}.
     * Images are cached.
     */
    public CompletableFuture<Image> getImage(long imageId) {
        logger.debug("Getting image with ID {}", imageId);

        CompletableFuture<Image> request;
        try {
            request = imagesCache.get(
                    imageId,
                    () -> {
                        logger.debug("Image with ID {} not in cache. Retrieving it", imageId);
                        return jsonApi.getImage(imageId);
                    }
            );
        } catch (ExecutionException e) {
            return CompletableFuture.failedFuture(e);
        }

        return request.whenComplete((image, error) -> {
            if (image == null) {
                logger.debug("Request to get image of ID {} failed. Invalidating cache entry", imageId, error);
                metadataCache.invalidate(imageId);
            }
        });
    }

    /**
     * Populate all orphaned images of this server to the list specified in parameter.
     * This function populates and doesn't return a list because the number of images can
     * be large, so this operation can take tens of seconds.
     * <p>
     * The list can be updated from any thread.
     *
     * @param children the list which should be populated by the orphaned images. It should
     *                 be possible to add elements to this list
     */
    public void populateOrphanedImagesIntoList(List<Image> children) {
        logger.debug("Populating orphaned images into list");

        synchronized (this) {
            areOrphanedImagesLoading.set(true);
        }

        getOrphanedImagesIds()
                .thenAccept(orphanedImageIds -> jsonApi.populateOrphanedImagesIntoList(children, orphanedImageIds))
                .whenComplete((v, error) -> {
                    synchronized (this) {
                        areOrphanedImagesLoading.set(false);
                    }

                    if (error != null) {
                        logger.error("Error when populating orphaned images", error);
                    }
                });
    }

    /**
     * @return whether orphaned images are currently being loaded.
     * This property may be updated from any thread
     */
    public ReadOnlyBooleanProperty areOrphanedImagesLoading() {
        return areOrphanedImagesLoading;
    }

    /**
     * See {@link JsonApi#getNumberOfOrphanedImagesLoaded()}.
     */
    public ReadOnlyIntegerProperty getNumberOfOrphanedImagesLoaded() {
        return jsonApi.getNumberOfOrphanedImagesLoaded();
    }

    /**
     * See {@link JsonApi#getScreens()}.
     */
    public CompletableFuture<List<Screen>> getScreens() {
        return jsonApi.getScreens();
    }

    /**
     * See {@link JsonApi#getOrphanedPlates()}.
     */
    public CompletableFuture<List<Plate>> getOrphanedPlates() {
        return jsonApi.getOrphanedPlates();
    }

    /**
     * See {@link JsonApi#getPlates(long)}.
     */
    public CompletableFuture<List<Plate>> getPlates(long screenId) {
        return jsonApi.getPlates(screenId);
    }

    /**
     * See {@link JsonApi#getPlateAcquisitions(long)}.
     */
    public CompletableFuture<List<PlateAcquisition>> getPlateAcquisitions(long plateId) {
        return jsonApi.getPlateAcquisitions(plateId);
    }

    /**
     * See {@link JsonApi#getWellsFromPlate(long)}.
     */
    public CompletableFuture<List<Well>> getWellsFromPlate(long plateId) {
        return jsonApi.getWellsFromPlate(plateId);
    }

    /**
     * See {@link JsonApi#getWellsFromPlateAcquisition(long,int)}.
     */
    public CompletableFuture<List<Well>> getWellsFromPlateAcquisition(long plateAcquisitionId, int wellSampleIndex) {
        return jsonApi.getWellsFromPlateAcquisition(plateAcquisitionId, wellSampleIndex);
    }

    /**
     * See {@link WebclientApi#getAnnotations(long, Class)}.
     */
    public CompletableFuture<AnnotationGroup> getAnnotations(
            long entityId,
            Class<? extends RepositoryEntity> entityClass
    ) {
        return webclientApi.getAnnotations(entityId, entityClass);
    }

    /**
     * See {@link WebclientApi#getSearchResults(SearchQuery)}.
     */
    public CompletableFuture<List<SearchResult>> getSearchResults(SearchQuery searchQuery) {
        return webclientApi.getSearchResults(searchQuery);
    }

    /**
     * See {@link WebclientApi#sendKeyValuePairs(long, Namespace, Map, boolean)}.
     */
    public CompletableFuture<Void> sendKeyValuePairs(
            long imageId,
            Namespace namespace,
            Map<String, String> keyValues,
            boolean replaceExisting
    ) {
        return webclientApi.sendKeyValuePairs(imageId, namespace, keyValues, replaceExisting);
    }

    /**
     * See {@link WebclientApi#changeImageName(long, String)}.
     */
    public CompletableFuture<Void> changeImageName(long imageId, String imageName) {
        return webclientApi.changeImageName(imageId, imageName);
    }

    /**
     * See {@link WebclientApi#changeChannelNames(long, List)}.
     */
    public CompletableFuture<Void> changeChannelNames(long imageId, List<String> channelsName) {
        return webclientApi.changeChannelNames(imageId, channelsName);
    }

    /**
     * Attempt to retrieve the icon of an OMERO entity.
     * <p>
     * Icons for orphaned folders, projects, datasets, images, screens, plates, and plate acquisitions can be retrieved.
     * <p>
     * Icons are cached.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param type the class of the entity whose icon is to be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the icon
     */
    public CompletableFuture<BufferedImage> getOmeroIcon(Class<? extends RepositoryEntity> type) {
        logger.debug("Getting OMERO icon {}", type);

        CompletableFuture<BufferedImage> request;

        try {
            request = omeroIconsCache.get(
                    type,
                    () -> {
                        logger.debug("OMERO icon {} not in cache. Retrieving it", type);

                        if (type.equals(Project.class)) {
                            return webGatewayApi.getProjectIcon();
                        } else if (type.equals(Dataset.class)) {
                            return webGatewayApi.getDatasetIcon();
                        } else if (type.equals(OrphanedFolder.class)) {
                            return webGatewayApi.getOrphanedFolderIcon();
                        } else if (type.equals(Image.class)) {
                            return webclientApi.getImageIcon();
                        } else if (type.equals(Screen.class)) {
                            return webclientApi.getScreenIcon();
                        } else if (type.equals(Plate.class)) {
                            return webclientApi.getPlateIcon();
                        } else if (type.equals(PlateAcquisition.class)) {
                            return webclientApi.getPlateAcquisitionIcon();
                        } else {
                            throw new IllegalArgumentException(String.format(
                                    "The provided type %s is not an orphaned folder, project, dataset, image, screen, plate or plate acquisition", type
                            ));
                        }
                    }
            );
        } catch (ExecutionException e) {
            return CompletableFuture.failedFuture(e);
        }

        return request.whenComplete((icon, error) -> {
            if (icon == null) {
                logger.debug("Request to get OMERO icon {} failed. Invalidating cache entry", type, error);
                omeroIconsCache.invalidate(type);
            }
        });
    }

    /**
     * {@link #getThumbnail(long, int)} with a size of
     * {@link #THUMBNAIL_SIZE}.
     */
    public CompletableFuture<BufferedImage> getThumbnail(long imageId) {
        return getThumbnail(imageId, THUMBNAIL_SIZE);
    }

    /**
     * See {@link WebGatewayApi#getThumbnail(long, int)}.
     * Thumbnails are cached in a cache of size {@link #THUMBNAIL_CACHE_SIZE}.
     */
    public CompletableFuture<BufferedImage> getThumbnail(long imageId, int size) {
        logger.debug("Getting thumbnail of image with ID {} and size {}", imageId, size);

        IdSizeWrapper key = new IdSizeWrapper(imageId, size);

        CompletableFuture<BufferedImage> request;
        try {
            request = thumbnailsCache.get(
                    key,
                    () -> {
                        logger.debug("Thumbnail of image with ID {} and size {} not in cache. Retrieving it", imageId, size);
                        return webGatewayApi.getThumbnail(imageId, size);
                    }
            );
        } catch (ExecutionException e) {
            return CompletableFuture.failedFuture(e);
        }

        return request.whenComplete((thumbnail, error) -> {
            if (thumbnail == null) {
                logger.debug("Request to get thumbnail of image with ID {} and size {} failed. Invalidating cache entry", imageId, size, error);
                thumbnailsCache.invalidate(key);
            }
        });
    }

    /**
     * See {@link WebGatewayApi#getImageMetadata(long)}.
     * Metadata is cached in a cache of size {@link #METADATA_CACHE_SIZE}.
     */
    public CompletableFuture<ImageServerMetadata> getImageMetadata(long imageId) {
        logger.debug("Getting metadata of image with ID {}", imageId);

        CompletableFuture<ImageServerMetadata> request;
        try {
            request = metadataCache.get(
                    imageId,
                    () -> {
                        logger.debug("Metadata of image with ID {} not in cache. Retrieving it", imageId);
                        return webGatewayApi.getImageMetadata(imageId);
                    }
            );
        } catch (ExecutionException e) {
            return CompletableFuture.failedFuture(e);
        }

        return request.whenComplete((metadata, error) -> {
            if (metadata == null) {
                logger.debug("Request to get metadata of image with ID {} failed. Invalidating cache entry", imageId, error);
                metadataCache.invalidate(imageId);
            }
        });
    }

    /**
     * See {@link WebGatewayApi#readTile(long, TileRequest, int, int, double)}.
     */
    public CompletableFuture<BufferedImage> readTile(
            long id,
            TileRequest tileRequest,
            int preferredTileWidth,
            int preferredTileHeight,
            double quality
    ) {
        return webGatewayApi.readTile(id, tileRequest, preferredTileWidth, preferredTileHeight, quality);
    }

    /**
     * See {@link WebGatewayApi#changeChannelColors(long, List, List)}.
     */
    public CompletableFuture<Void> changeChannelColors(long imageID, List<Integer> newChannelColors) {
        logger.debug("Changing channel colors of image with ID {} to {}", imageID, newChannelColors);

        return getImageSettings(imageID).thenCompose(imageSettings -> {
            logger.debug("Got image settings {} from image with ID {}. Now changing channel colors to {}", imageSettings, imageID, newChannelColors);
            return webGatewayApi.changeChannelColors(imageID, newChannelColors, imageSettings.getChannelSettings());
        });
    }

    /**
     * See {@link WebGatewayApi#changeChannelDisplayRanges(long, List, List)}.
     */
    public CompletableFuture<Void> changeChannelDisplayRanges(long imageID, List<ChannelSettings> newChannelSettings) {
        logger.debug("Changing channel display ranges of image with ID {} to {}", imageID, newChannelSettings);

        return getImageSettings(imageID).thenCompose(imageSettings -> {
            logger.debug("Got image settings {} from image with ID {}. Now changing channel display ranges to {}", imageSettings, imageID, newChannelSettings);
            return webGatewayApi.changeChannelDisplayRanges(imageID, newChannelSettings, imageSettings.getChannelSettings());
        });
    }

    /**
     * See {@link JsonApi#getShapes(long, long)}.
     */
    public CompletableFuture<List<Shape>> getShapes(long id, long userId) {
        return jsonApi.getShapes(id, userId);
    }

    /**
     * Delete all shapes of the provided image.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param imageId the ID of the image containing the shapes to delete
     * @param userIds the list of IDs of the users that should own the shapes to delete
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     */
    public CompletableFuture<Void> deleteShapes(long imageId, List<Long> userIds) {
        logger.debug("Deleting shapes of image with ID {} belonging to users with ID {}", imageId, userIds);

        return CompletableFuture.supplyAsync(() -> userIds.stream()
                        .map(userId -> getShapes(imageId, userId))
                        .map(CompletableFuture::join)
                        .flatMap(List::stream)
                        .toList()
        ).thenCompose(shapes -> {
            logger.debug("Got shapes {} belonging to users with ID {} for image with ID {}. Deleting them now", shapes, userIds, imageId);
            return iViewerApi.deleteShapes(imageId, shapes, jsonApi.getToken());
        });
    }

    /**
     * See {@link IViewerApi#addShapes(long, List, String)}.
     */
    public CompletableFuture<Void> addShapes(long imageId, List<? extends Shape> shapesToAdd) {
        return iViewerApi.addShapes(imageId, shapesToAdd, jsonApi.getToken());
    }

    /**
     * See {@link IViewerApi#getImageSettings(long)}.
     */
    public CompletableFuture<ImageSettings> getImageSettings(long imageId) {
        return iViewerApi.getImageSettings(imageId);
    }

    /**
     * See {@link WebclientApi#sendAttachment(long, Class, String, String)}.
     */
    public CompletableFuture<Void> sendAttachment(
            long entityId,
            Class<? extends RepositoryEntity> entityClass,
            String attachmentName,
            String attachmentContent
    ) {
        return webclientApi.sendAttachment(entityId, entityClass, attachmentName, attachmentContent);
    }

    /**
     * See {@link WebclientApi#deleteAttachments(long, Class, List)}.
     */
    public CompletableFuture<Void> deleteAttachments(long entityId, Class<? extends RepositoryEntity> entityClass, List<String> ownerFullNames) {
        return webclientApi.deleteAttachments(entityId, entityClass, ownerFullNames);
    }

    private CompletableFuture<List<URI>> getImagesURIOfProject(long projectID) {
        logger.debug("Finding image URIs contained in project with ID {}", projectID);

        return getDatasets(projectID).thenApply(datasets -> datasets.stream()
                .map(dataset -> getImagesURIOfDataset(dataset.getId()))
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList()
        );
    }

    public CompletableFuture<List<URI>> getImagesURIOfDataset(long datasetID) {
        logger.debug("Finding image URIs contained in dataset with ID {}", datasetID);

        return getImages(datasetID).thenApply(images -> images.stream()
                .map(this::getItemURI)
                .map(URI::create)
                .toList()
        );
    }
}
