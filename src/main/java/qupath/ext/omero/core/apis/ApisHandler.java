package qupath.ext.omero.core.apis;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableIntegerValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.entities.Namespace;
import qupath.ext.omero.core.entities.annotations.AnnotationGroup;
import qupath.ext.omero.core.entities.image.ChannelSettings;
import qupath.ext.omero.core.entities.image.ImageSettings;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.repositoryentities2.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Image;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.PlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Well;
import qupath.ext.omero.core.entities.search.SearchQuery;
import qupath.ext.omero.core.entities.search.SearchResultWithParentInfo;
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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

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
    private static final Pattern PROJECT_PATTERN = Pattern.compile("/webclient/\\?show=project-(\\d+)");
    private static final Pattern DATASET_PATTERN = Pattern.compile("/webclient/\\?show=dataset-(\\d+)");
    private static final Pattern WEBCLIENT_IMAGE_PATTERN = Pattern.compile("/webclient/\\?show=image-(\\d+)");
    private static final Pattern WEBCLIENT_IMAGE_PATTERN_ALTERNATE = Pattern.compile("/webclient/img_detail/(\\d+)");
    private static final Pattern WEBGATEWAY_IMAGE_PATTERN = Pattern.compile("/webgateway/img_detail/(\\d+)");
    private static final Pattern IVIEWER_IMAGE_PATTERN = Pattern.compile("/iviewer/\\?images=(\\d+)");
    private static final Pattern WELL_PATTERN = Pattern.compile("/webclient/\\?show=well-(\\d+)");
    private static final Pattern PLATE_ACQUISITION_PATTERN = Pattern.compile("/webclient/\\?show=run-(\\d+)");
    private static final Pattern PLATE_PATTERN = Pattern.compile("/webclient/\\?show=plate-(\\d+)");
    private static final Pattern SCREEN_PATTERN = Pattern.compile("/webclient/\\?show=screen-(\\d+)");
    private final BooleanProperty areOrphanedImagesLoading = new SimpleBooleanProperty(false);
    //TODO: put caches in their specific API
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
     * Attempt to get all parents of an image. Only {@link Screen}, {@link Plate}, {@link PlateAcquisition}, {@link Well},
     * {@link Project}, and {@link Dataset} entities are considered.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param imageId the ID of the image whose parents should be retrieved
     * @return the list of parents of the provided image
     */
    public CompletableFuture<List<ServerEntity>> getParentsOfImage(long imageId) {
        return webclientApi.getParentsOfImage(imageId).thenApplyAsync(serverEntities -> {
            logger.debug("Got parents {}. Fetching now more information on them", serverEntities);

            return serverEntities.stream()
                    .map(serverEntity -> {
                        if (serverEntity.getClass().equals(Screen.class)) {
                            return jsonApi.getScreen(serverEntity.getId());
                        } else if (serverEntity.getClass().equals(Plate.class)) {
                            return jsonApi.getPlate(serverEntity.getId());
                        } else if (serverEntity.getClass().equals(PlateAcquisition.class)) {
                            return jsonApi.getPlateAcquisition(serverEntity.getId());
                        } else if (serverEntity.getClass().equals(Well.class)) {
                            return jsonApi.getWell(serverEntity.getId());
                        } else if (serverEntity.getClass().equals(Project.class)) {
                            return jsonApi.getProject(serverEntity.getId());
                        } else if (serverEntity.getClass().equals(Dataset.class)) {
                            return jsonApi.getDataset(serverEntity.getId());
                        } else {
                            throw new IllegalArgumentException(String.format(
                                    "The provided entity %s was not recognized",
                                    serverEntity
                            ));
                        }
                    })
                    .map(CompletableFuture::join)
                    .map(serverEntity -> (ServerEntity) serverEntity)
                    .toList();
        });
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
     * Parse an OMERO server entity from a URI. The returned entity will be empty except for its ID.
     * In other words, this function returns an entity ID and an entity class.
     * <p>
     * This function mar recognize projects, datasets, images, wells, plate acquisitions, plates, and screens.
     *
     * @param uri the URI that is supposed to contain the entity. It can be URL encoded
     * @return the entity (whose only correct attribute is its ID), or an empty Optional if it was not found
     */
    public static Optional<ServerEntity> parseEntity(URI uri) {
        logger.debug("Finding entity in {}...", uri);

        Map<Pattern, Function<Long, ServerEntity>> entityCreator = Map.of(
                PROJECT_PATTERN, Project::new,
                DATASET_PATTERN, Dataset::new,
                WEBCLIENT_IMAGE_PATTERN, Image::new,
                WEBCLIENT_IMAGE_PATTERN_ALTERNATE, Image::new,
                WEBGATEWAY_IMAGE_PATTERN, Image::new,
                IVIEWER_IMAGE_PATTERN, Image::new,
                WELL_PATTERN, Well::new,
                PLATE_ACQUISITION_PATTERN, PlateAcquisition::new,
                PLATE_PATTERN, Plate::new,
                SCREEN_PATTERN, Screen::new
        );
        String entityUrl = URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);

        for (var entry: entityCreator.entrySet()) {
            Matcher matcher = entry.getKey().matcher(entityUrl);

            if (matcher.find()) {
                String idValue = matcher.group(1);
                try {
                    ServerEntity serverEntity = entry.getValue().apply(Long.parseLong(idValue));
                    logger.debug(
                            "Found {} with ID {} in {} with matcher {}",
                            serverEntity.getClass(),
                            serverEntity.getId(),
                            entityUrl,
                            matcher
                    );
                    return Optional.of(serverEntity);
                } catch (NumberFormatException e) {
                    logger.debug("Found entity ID {} in {} with matcher {} but it is not an integer. Skipping it", idValue, entityUrl, matcher, e);
                }
            } else {
                logger.debug("Entity not found in {} with matcher {}", entityUrl, matcher);
            }
        }

        logger.debug("Entity not found in {}", entityUrl);
        return Optional.empty();
    }

    /**
     * Attempt to retrieve the image URIs indicated by the provided entity URI.
     * <ul>
     *     <li>If the entity is a dataset, the URIs of the children of this dataset (which are images) are returned.</li>
     *     <li>If the entity is a project, the URIs of each child of the datasets of this project are returned.</li>
     *     <li>If the entity is an image, the input URI is returned.</li>
     *     <li>If the entity is a well, the URIs of the child images of the well are returned.</li>
     *     <li>If the entity is a plate acquisition, the URIs of the child images of each well contained in the plate acquisition are returned.</li>
     *     <li>If the entity is a plate, the URIs of the child images of each well contained in the plate are returned.</li>
     *     <li>If the entity is a screen, the URIs of the child images of each well contained in the child plates of the screen are returned.</li>
     *     <li>Else, an error is returned.</li>
     * </ul>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request or the conversion failed for example).
     *
     * @param entityUri the URI of the entity whose images should be retrieved. It can be URL encoded
     * @return a CompletableFuture (that may complete exceptionally) with the list described above
     */
    public CompletableFuture<List<URI>> getImageUrisFromEntityURI(URI entityUri) {
        logger.debug("Finding image URIs indicated by {}...", entityUri);

        Map<Class<? extends ServerEntity>, Function<Long, CompletableFuture<List<URI>>>> classToUrisProvider = Map.of(
                Project.class, this::getImageUrisOfProject,
                Dataset.class, this::getImageUrisOfDataset,
                Image.class, entityId -> CompletableFuture.completedFuture(List.of(entityUri)),
                Well.class, entityId -> getImageUrisOfWell(entityId, -1),
                PlateAcquisition.class, this::getImageUrisOfPlateAcquisition,
                Plate.class, this::getImageUrisOfPlate,
                Screen.class, this::getImageUrisOfScreen
        );

        return parseEntity(entityUri).map(entity -> {
            if (classToUrisProvider.containsKey(entity.getClass())) {
                logger.debug("{} refers to a {}. Retrieving all images URIs belonging to it", entityUri, entity.getClass());
                return classToUrisProvider.get(entity.getClass()).apply(entity.getId());
            } else {
                return null;
            }
        }).orElse(CompletableFuture.failedFuture(new IllegalArgumentException(
                String.format("The provided URI %s does not represent a project, dataset, image, well, plate acquisition, plate, or screen", entityUri)
        )));
    }

    /**
     * See {@link RequestSender#getImage(URI)}.
     */
    public CompletableFuture<BufferedImage> getImage(URI uri) {
        return requestSender.getImage(uri);
    }

    /**
     * See {@link WebclientApi#getEntityUri(ServerEntity)}.
     */
    public String getEntityUri(ServerEntity entity) {
        return webclientApi.getEntityUri(entity);
    }

    /**
     * See {@link JsonApi#getNumberOfEntitiesLoading}.
     */
    public ObservableIntegerValue getNumberOfEntitiesLoading() {
        return jsonApi.getNumberOfEntitiesLoading();
    }

    /**
     * See {@link WebGatewayApi#getNumberOfThumbnailsLoading()}.
     */
    public ObservableIntegerValue getNumberOfThumbnailsLoading() {
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

    public CompletableFuture<List<Project>> getProjects(long ownerId, long groupId) {
        return jsonApi.getProjects(ownerId, groupId);
    }

    public CompletableFuture<List<Dataset>> getDatasets(long projectId, long ownerId, long groupId) {
        return jsonApi.getDatasets(projectId, ownerId, groupId);
    }

    public CompletableFuture<List<Dataset>> getOrphanedDatasets(long ownerId, long groupId) {
        return jsonApi.getOrphanedDatasets(ownerId, groupId);
    }

    public CompletableFuture<List<Image>> getImages(long datasetId, long ownerId, long groupId) {
        return jsonApi.getImages(datasetId, ownerId, groupId);
    }

    public CompletableFuture<List<Image>> getOrphanedImages(long ownerId, long groupId) {
        return jsonApi.getOrphanedImages(ownerId, groupId);
    }

    public CompletableFuture<List<Screen>> getScreens(long ownerId, long groupId) {
        return jsonApi.getScreens(ownerId, groupId);
    }

    public CompletableFuture<List<Plate>> getPlates(long screenId, long ownerId, long groupId) {
        return jsonApi.getPlates(screenId, ownerId, groupId);
    }

    public CompletableFuture<List<Plate>> getOrphanedPlates(long ownerId, long groupId) {
        return jsonApi.getOrphanedPlates(ownerId, groupId);
    }

    public CompletableFuture<List<PlateAcquisition>> getPlateAcquisitions(long plateId, long ownerId, long groupId, int numberOfWells) {
        return jsonApi.getPlateAcquisitions(plateId, ownerId, groupId, numberOfWells);
    }

    public CompletableFuture<List<Well>> getWellsFromPlate(long plateId, long ownerId, long groupId) {
        return jsonApi.getWellsFromPlate(plateId, ownerId, groupId);
    }

    public CompletableFuture<List<Well>> getWellsFromPlateAcquisition(long plateAcquisitionId, long ownerId, long groupId, int wellSampleIndex) {
        return jsonApi.getWellsFromPlateAcquisition(plateAcquisitionId, ownerId, groupId, wellSampleIndex);
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
                .thenAcceptAsync(orphanedImageIds -> jsonApi.populateOrphanedImagesIntoList(children, orphanedImageIds))
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
    public ObservableBooleanValue areOrphanedImagesLoading() {
        return areOrphanedImagesLoading;
    }

    /**
     * See {@link JsonApi#getNumberOfOrphanedImagesLoaded()}.
     */
    public ObservableIntegerValue getNumberOfOrphanedImagesLoaded() {
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
     * See {@link JsonApi#getWell(long)}.
     */
    public CompletableFuture<Well> getWell(long wellId) {
        return jsonApi.getWell(wellId);
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
     * Same as {@link WebclientApi#getSearchResults(SearchQuery)}, but with additional information on the parents if the
     * entity is an image
     */
    public CompletableFuture<List<SearchResultWithParentInfo>> getSearchResults(SearchQuery searchQuery) {
        return webclientApi.getSearchResults(searchQuery).thenApplyAsync(searchResults -> {
            logger.debug("Got search results {}. Getting information on the parents of images", searchResults);

            return searchResults.stream()
                    .map(searchResult -> {
                        if (searchResult.getType().isPresent() && searchResult.getType().get().equals(Image.class)) {
                            logger.debug("{} refers to an image. Searching parents of it", searchResult);

                            return getParentsOfImage(searchResult.id()).handle((parents, error) -> {
                                if (parents == null) {
                                    logger.debug("Cannot retrieve parents of {}. Considering it doesn't have any", searchResult, error);
                                    return new SearchResultWithParentInfo(searchResult);
                                }

                                logger.debug("Got parents {} for image {}", parents, searchResult);
                                return new SearchResultWithParentInfo(
                                        searchResult,
                                        getEntityOfTypeInList(parents, Project.class),
                                        getEntityOfTypeInList(parents, Dataset.class),
                                        getEntityOfTypeInList(parents, Screen.class),
                                        getEntityOfTypeInList(parents, Plate.class),
                                        getEntityOfTypeInList(parents, PlateAcquisition.class),
                                        getEntityOfTypeInList(parents, Well.class)
                                );
                            });
                        } else {
                            logger.debug("{} does not refer to an image. Not fetching parent information", searchResult);
                            return CompletableFuture.completedFuture(new SearchResultWithParentInfo(searchResult));
                        }
                    })
                    .map(CompletableFuture::join)
                    .toList();
        });
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
     * Icons for orphaned folders, projects, datasets, images, screens, plates, plate acquisitions and wells can be retrieved.
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
        logger.trace("Getting OMERO icon {}", type);

        CompletableFuture<BufferedImage> request;

        try {
            request = omeroIconsCache.get(
                    type,
                    () -> {
                        logger.trace("OMERO icon {} not in cache. Retrieving it", type);

                        if (type.equals(Project.class)) {
                            return webGatewayApi.getProjectIcon();
                        } else if (type.equals(Dataset.class)) {
                            return webGatewayApi.getDatasetIcon();
                        } else if (type.equals(OrphanedFolder.class)) {
                            return webGatewayApi.getOrphanedFolderIcon();
                        } else if (type.equals(Well.class)) {
                            return webGatewayApi.getWellIcon();
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
        logger.trace("Getting thumbnail of image with ID {} and size {}", imageId, size);

        IdSizeWrapper key = new IdSizeWrapper(imageId, size);

        CompletableFuture<BufferedImage> request;
        try {
            request = thumbnailsCache.get(
                    key,
                    () -> {
                        logger.trace("Thumbnail of image with ID {} and size {} not in cache. Retrieving it", imageId, size);
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
     * See {@link JsonApi#reLogin()}.
     */
    public CompletableFuture<Void> reLogin() {
        return jsonApi.reLogin();
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

    private CompletableFuture<List<URI>> getImageUrisOfProject(long projectId) {
        logger.debug("Finding image URIs contained in project with ID {}", projectId);

        return getDatasets(projectId).thenApply(datasets -> {
            logger.debug("Found datasets {} belonging to project with ID {}. Now retrieving image URIs of those datasets", datasets, projectId);

            return datasets.stream()
                    .map(dataset -> getImageUrisOfDataset(dataset.getId()))
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .distinct()
                    .toList();
        });
    }

    private CompletableFuture<List<URI>> getImageUrisOfDataset(long datasetId) {
        logger.debug("Finding image URIs contained in dataset with ID {}", datasetId);

        return getImages(datasetId).thenApply(images -> {
            logger.debug("Found images {} belonging to dataset with ID {}. Now creating image URIs of them", images, datasetId);

            return images.stream()
                    .map(this::getEntityUri)
                    .map(URI::create)
                    .distinct()
                    .toList();
        });
    }

    private CompletableFuture<List<URI>> getImageUrisOfWell(long wellId, long plateAcquisitionOwnerId) {
        logger.debug("Finding image URIs contained in well with ID {} and belonging to plate acquisition with ID {}", wellId, plateAcquisitionOwnerId);

        return getWell(wellId).thenApply(well -> {
            logger.debug("Got well {}. Now getting image URIs of it", well);

            return well.getImageIds(plateAcquisitionOwnerId).stream()
                    .map(Image::new)
                    .map(this::getEntityUri)
                    .map(URI::create)
                    .distinct()
                    .toList();
        });
    }

    private CompletableFuture<List<URI>> getImageUrisOfPlateAcquisition(long plateAcquisitionId) {
        logger.debug("Finding image URIs contained in plate acquisition with ID {}", plateAcquisitionId);

        return jsonApi.getPlateAcquisition(plateAcquisitionId).thenApply(plateAcquisition -> {
            int minWellSampleIndex = plateAcquisition.getMinWellSampleIndex().orElseThrow(() -> new NoSuchElementException(String.format(
                    "No min well sample index found in %s",
                    plateAcquisition
            )));
            int maxWellSampleIndex = plateAcquisition.getMaxWellSampleIndex().orElseThrow(() -> new NoSuchElementException(String.format(
                    "No max well sample index found in %s",
                    plateAcquisition
            )));
            logger.debug(
                    "Got plate acquisition {}. Now getting wells of it with well sample indices between {} and {}",
                    plateAcquisition,
                    minWellSampleIndex,
                    maxWellSampleIndex
            );

            return IntStream.range(minWellSampleIndex, maxWellSampleIndex+1)
                    .mapToObj(i -> getWellsFromPlateAcquisition(plateAcquisitionId, i))
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .map(ServerEntity::getId)
                    .map(wellId -> getImageUrisOfWell(wellId, plateAcquisitionId))
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .distinct()
                    .toList();
        });
    }

    private CompletableFuture<List<URI>> getImageUrisOfPlate(long plateId) {
        logger.debug("Finding image URIs contained in plate with ID {}", plateId);

        return getWellsFromPlate(plateId).thenApplyAsync(wells -> {
            logger.debug("Found wells {} belonging to plate with ID {}. Now finding image URIs of them", wells, plateId);

            return wells.stream()
                    .map(ServerEntity::getId)
                    .map(wellId -> getImageUrisOfWell(wellId, -1))
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .distinct()
                    .toList();
        });
    }

    private CompletableFuture<List<URI>> getImageUrisOfScreen(long screenId) {
        logger.debug("Finding image URIs contained in screen with ID {}", screenId);

        return getPlates(screenId).thenApplyAsync(plates -> {
            logger.debug("Found plates {} belonging to screen with ID {}. Now finding image URIs of them", plates, screenId);

            return plates.stream()
                    .map(ServerEntity::getId)
                    .map(this::getImageUrisOfPlate)
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .distinct()
                    .toList();
        });
    }

    private static <T extends ServerEntity> T getEntityOfTypeInList(List<ServerEntity> entities, Class<T> type) {
        return entities.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findAny()
                .orElse(null);
    }
}
