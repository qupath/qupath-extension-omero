package qupath.ext.omero.core.apis;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import javafx.beans.value.ObservableIntegerValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.iviewer.IViewerApi;
import qupath.ext.omero.core.apis.iviewer.imageentities.ImageData;
import qupath.ext.omero.core.apis.json.JsonApi;
import qupath.ext.omero.core.apis.webclient.EntityType;
import qupath.ext.omero.core.apis.webclient.WebclientApi;
import qupath.ext.omero.core.apis.webclient.annotations.Annotation;
import qupath.ext.omero.core.apis.webgateway.WebGatewayApi;
import qupath.ext.omero.core.apis.webclient.Namespace;
import qupath.ext.omero.core.apis.webclient.SimpleServerEntity;
import qupath.ext.omero.core.apis.commonentities.ChannelSettings;
import qupath.ext.omero.core.apis.json.permissions.ExperimenterGroup;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Image;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Well;
import qupath.ext.omero.core.apis.webclient.search.SearchQuery;
import qupath.ext.omero.core.apis.webclient.search.SearchResultWithParentInfo;
import qupath.ext.omero.core.apis.commonentities.shapes.Shape;
import qupath.lib.common.ThreadTools;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final RequestSender requestSender = new RequestSender();
    private final ExecutorService executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            ThreadTools.createThreadFactory("apis-handler-", true)
    );
    private final URI webServerUri;
    private final Credentials credentials;
    private final JsonApi jsonApi;
    private final WebclientApi webclientApi;
    private final WebGatewayApi webGatewayApi;
    private final IViewerApi iViewerApi;
    private final LoadingCache<EntityType, BufferedImage> omeroIconsCache;

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
     * @throws NullPointerException if the server doesn't return all necessary information
     */
    public ApisHandler(URI webServerUri, Credentials credentials) throws URISyntaxException, ExecutionException, InterruptedException {
        this.webServerUri = webServerUri;
        this.credentials = credentials;
        this.jsonApi = new JsonApi(webServerUri, requestSender, credentials);
        this.webclientApi = new WebclientApi(webServerUri, requestSender, jsonApi.getToken());
        this.webGatewayApi = new WebGatewayApi(webServerUri, requestSender, jsonApi.getToken());
        this.iViewerApi = new IViewerApi(webServerUri, requestSender, jsonApi.getToken());

        this.omeroIconsCache = CacheBuilder.newBuilder()
                .build(new CacheLoader<>() {
                    @Override
                    public BufferedImage load(EntityType entityType) throws ExecutionException, InterruptedException {
                        logger.debug("Fetching icon of entity {} (not already in cache)", entityType);

                        return switch (entityType) {
                            case SCREEN -> webclientApi.getScreenIcon().get();
                            case PLATE -> webclientApi.getPlateIcon().get();
                            case PLATE_ACQUISITION -> webclientApi.getPlateAcquisitionIcon().get();
                            case WELL -> webGatewayApi.getWellIcon().get();
                            case PROJECT -> webGatewayApi.getProjectIcon().get();
                            case DATASET -> webGatewayApi.getDatasetIcon().get();
                            case IMAGE -> webclientApi.getImageIcon().get();
                        };
                    }
                });
    }

    /**
     * Close the connection.
     * This may take a moment as an HTTP request is made.
     *
     * @throws Exception when an error occurs when closing the connection
     */
    @Override
    public void close() throws Exception {
        iViewerApi.close();
        webGatewayApi.close();
        webclientApi.close();
        jsonApi.close();
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
     * See {@link #getServerAddress()} for more information.
     *
     * @return the URI of the web server
     */
    public URI getWebServerUri() {
        return webServerUri;
    }

    /**
     * @return the credentials used for authenticating to this server
     */
    public Credentials getCredentials() {
        return credentials;
    }

    /**
     * See {@link JsonApi#getServerAddress()}.
     */
    public String getServerAddress() {
        return jsonApi.getServerAddress();
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
        return webclientApi.getParentsOfImage(imageId).thenApplyAsync(
                simpleServerEntities -> {
                    logger.debug("Got parents {}. Fetching now more information on them", simpleServerEntities);

                    return simpleServerEntities.stream()
                            .map(serverEntity -> switch (serverEntity.entityType()) {
                                case SCREEN -> jsonApi.getScreen(serverEntity.id());
                                case PLATE -> jsonApi.getPlate(serverEntity.id());
                                case PLATE_ACQUISITION -> jsonApi.getPlateAcquisition(serverEntity.id());
                                case WELL -> jsonApi.getWell(serverEntity.id());
                                case PROJECT -> jsonApi.getProject(serverEntity.id());
                                case DATASET -> jsonApi.getDataset(serverEntity.id());
                                case IMAGE -> jsonApi.getImage(serverEntity.id());
                            })
                            .map(CompletableFuture::join)
                            .map(serverEntity -> (ServerEntity) serverEntity)
                            .toList();
                },
                executorService
        );
    }

    /**
     * See {@link JsonApi#getDefaultGroupId()}.
     */
    public Optional<Long> getDefaultGroupId() {
        return jsonApi.getDefaultGroupId();
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
     * Parse an OMERO server entity from a URI.
     * <p>
     * This function may only recognize entity types of {@link EntityType}.
     *
     * @param uri the URI that is supposed to contain the entity. It can be URL encoded
     * @return the entity, or an empty Optional if it was not found
     */
    public static Optional<SimpleServerEntity> parseEntity(URI uri) {
        return EntityParser.parseUri(uri);
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

        return parseEntity(entityUri).map(entity -> switch (entity.entityType()) {
            case PROJECT -> getImageUrisOfProject(entity.id());
            case DATASET -> getImageUrisOfDataset(entity.id());
            case IMAGE -> CompletableFuture.completedFuture(List.of(entityUri));
            case SCREEN -> getImageUrisOfScreen(entity.id());
            case PLATE -> getImageUrisOfPlate(entity.id());
            case PLATE_ACQUISITION -> getImageUrisOfPlateAcquisition(entity.id());
            case WELL -> getImageUrisOfWell(entity.id(), -1);
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
     * See {@link WebclientApi#getEntityUri(SimpleServerEntity)}.
     */
    public String getEntityUri(SimpleServerEntity entity) {
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
     * See {@link JsonApi#getGroups(long)}.
     */
    public CompletableFuture<List<ExperimenterGroup>> getGroups(long userId) {
        return jsonApi.getGroups(userId);
    }

    /**
     * Same as {@link #getGroups(long)}, but to get all groups of the server
     */
    public CompletableFuture<List<ExperimenterGroup>> getGroups() {
        return jsonApi.getGroups(-1);
    }

    /**
     * See {@link JsonApi#getProjects(long, long)}.
     */
    public CompletableFuture<List<Project>> getProjects(long experimenterId, long groupId) {
        return jsonApi.getProjects(experimenterId, groupId);
    }

    /**
     * See {@link JsonApi#getDatasets(long, long, long)}.
     */
    public CompletableFuture<List<Dataset>> getDatasets(long projectId, long experimenterId, long groupId) {
        return jsonApi.getDatasets(projectId, experimenterId, groupId);
    }

    /**
     * See {@link JsonApi#getOrphanedDatasets(long, long)}.
     */
    public CompletableFuture<List<Dataset>> getOrphanedDatasets(long experimenterId, long groupId) {
        return jsonApi.getOrphanedDatasets(experimenterId, groupId);
    }

    /**
     * See {@link JsonApi#getImages(long, long, long)}.
     */
    public CompletableFuture<List<Image>> getImages(long datasetId, long experimenterId, long groupId) {
        return jsonApi.getImages(datasetId, experimenterId, groupId);
    }

    /**
     * See {@link JsonApi#getImage(long)}.
     */
    public CompletableFuture<Image> getImage(long imageId) {
        return jsonApi.getImage(imageId);
    }

    /**
     * See {@link JsonApi#getOrphanedImages(long, long)}.
     */
    public CompletableFuture<List<Image>> getOrphanedImages(long experimenterId, long groupId) {
        return jsonApi.getOrphanedImages(experimenterId, groupId);
    }

    /**
     * See {@link JsonApi#getScreens(long, long)}.
     */
    public CompletableFuture<List<Screen>> getScreens(long experimenterId, long groupId) {
        return jsonApi.getScreens(experimenterId, groupId);
    }

    /**
     * See {@link JsonApi#getPlates(long, long, long)}.
     */
    public CompletableFuture<List<Plate>> getPlates(long screenId, long experimenterId, long groupId) {
        return jsonApi.getPlates(screenId, experimenterId, groupId);
    }

    /**
     * See {@link JsonApi#getOrphanedPlates(long, long)}.
     */
    public CompletableFuture<List<Plate>> getOrphanedPlates(long experimenterId, long groupId) {
        return jsonApi.getOrphanedPlates(experimenterId, groupId);
    }

    /**
     * See {@link JsonApi#getPlateAcquisitions(long, long, long, int)}.
     */
    public CompletableFuture<List<PlateAcquisition>> getPlateAcquisitions(long plateId, long experimenterId, long groupId, int numberOfWells) {
        return jsonApi.getPlateAcquisitions(plateId, experimenterId, groupId, numberOfWells);
    }

    /**
     * See {@link JsonApi#getWellsFromPlate(long, long, long)}.
     */
    public CompletableFuture<List<Well>> getWellsFromPlate(long plateId, long experimenterId, long groupId) {
        return jsonApi.getWellsFromPlate(plateId, experimenterId, groupId);
    }

    /**
     * See {@link JsonApi#getWellsFromPlateAcquisition(long, long, long, int)}.
     */
    public CompletableFuture<List<Well>> getWellsFromPlateAcquisition(long plateAcquisitionId, long experimenterId, long groupId, int wellSampleIndex) {
        return jsonApi.getWellsFromPlateAcquisition(plateAcquisitionId, experimenterId, groupId, wellSampleIndex);
    }

    /**
     * See {@link JsonApi#getWell(long)}.
     */
    public CompletableFuture<Well> getWell(long wellId) {
        return jsonApi.getWell(wellId);
    }

    /**
     * See {@link WebclientApi#getAnnotations(SimpleServerEntity)}.
     */
    public CompletableFuture<List<Annotation>> getAnnotations(SimpleServerEntity entity) {
        return webclientApi.getAnnotations(entity);
    }

    /**
     * Same as {@link WebclientApi#getSearchResults(SearchQuery)}, but with additional information on the parents if the
     * entity is an image.
     */
    public CompletableFuture<List<SearchResultWithParentInfo>> getSearchResults(SearchQuery searchQuery) {
        return webclientApi.getSearchResults(searchQuery).thenApplyAsync(
                searchResults -> {
                    logger.debug("Got search results {}. Getting information on the parents of images", searchResults);

                    return searchResults.stream()
                            .map(searchResult -> {
                                if (searchResult.getType().isPresent() && searchResult.getType().get().equals(EntityType.IMAGE)) {
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
                },
                executorService
        );
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
     * Icons are cached.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param type the class of the entity whose icon is to be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the icon
     */
    public CompletableFuture<BufferedImage> getOmeroIcon(EntityType type) {
        logger.trace("Getting OMERO icon {}", type);

        return CompletableFuture.supplyAsync(() -> omeroIconsCache.getUnchecked(type), executorService);
    }

    /**
     * See {@link WebGatewayApi#getOrphanedFolderIcon()}.
     */
    public CompletableFuture<BufferedImage> getOrphanedFolderIcon() {
        logger.trace("Getting OMERO orphaned folder icon");

        return webGatewayApi.getOrphanedFolderIcon();
    }

    /**
     * {@link #getThumbnail(long, int)} with a size of {@link #THUMBNAIL_SIZE}.
     */
    public CompletableFuture<BufferedImage> getThumbnail(long imageId) {
        return getThumbnail(imageId, THUMBNAIL_SIZE);
    }

    /**
     * See {@link WebGatewayApi#getThumbnail(long, int)}.
     */
    public CompletableFuture<BufferedImage> getThumbnail(long imageId, int size) {
        return webGatewayApi.getThumbnail(imageId, size);
    }

    /**
     * See {@link WebGatewayApi#getImageMetadata(long)}.
     */
    public CompletableFuture<ImageServerMetadata> getImageMetadata(long imageId) {
        return webGatewayApi.getImageMetadata(imageId);
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
    public CompletableFuture<Void> changeChannelColors(long imageId, List<Integer> newChannelColors) {
        logger.debug("Changing channel colors of image with ID {} to {}", imageId, newChannelColors);

        return getImageData(imageId).thenCompose(imageData -> {
            logger.debug("Got image settings {} from image with ID {}. Now changing channel colors to {}", imageData, imageId, newChannelColors);
            return webGatewayApi.changeChannelColors(imageId, newChannelColors, imageData.getChannelSettings());
        });
    }

    /**
     * See {@link WebGatewayApi#changeChannelDisplayRanges(long, List, List)}.
     */
    public CompletableFuture<Void> changeChannelDisplayRanges(long imageID, List<ChannelSettings> newChannelSettings) {
        logger.debug("Changing channel display ranges of image with ID {} to {}", imageID, newChannelSettings);

        return getImageData(imageID).thenCompose(imageData -> {
            logger.debug("Got image settings {} from image with ID {}. Now changing channel display ranges to {}", imageData, imageID, newChannelSettings);
            return webGatewayApi.changeChannelDisplayRanges(imageID, newChannelSettings, imageData.getChannelSettings());
        });
    }

    /**
     * See {@link JsonApi#getShapes(long, long)}.
     */
    public CompletableFuture<List<Shape>> getShapes(long imageId, long userId) {
        return jsonApi.getShapes(imageId, userId);
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

        return CompletableFuture.supplyAsync(
                () -> userIds.stream()
                        .map(userId -> getShapes(imageId, userId))
                        .map(CompletableFuture::join)
                        .flatMap(List::stream)
                        .toList(),
                executorService
        ).thenCompose(shapes -> {
            logger.debug("Got shapes {} belonging to users with ID {} for image with ID {}. Deleting them now", shapes, userIds, imageId);
            return iViewerApi.deleteShapes(imageId, shapes);
        });
    }

    /**
     * See {@link IViewerApi#addShapes(long, List)}.
     */
    public CompletableFuture<Void> addShapes(long imageId, List<? extends Shape> shapesToAdd) {
        return iViewerApi.addShapes(imageId, shapesToAdd);
    }

    /**
     * See {@link IViewerApi#getImageData(long)}.
     */
    public CompletableFuture<ImageData> getImageData(long imageId) {
        return iViewerApi.getImageData(imageId);
    }

    /**
     * See {@link WebclientApi#sendAttachment(SimpleServerEntity, String, String)}.
     */
    public CompletableFuture<Void> sendAttachment(SimpleServerEntity entity, String attachmentName, String attachmentContent) {
        return webclientApi.sendAttachment(entity, attachmentName, attachmentContent);
    }

    /**
     * See {@link WebclientApi#deleteAttachments(SimpleServerEntity, List)}.
     */
    public CompletableFuture<Void> deleteAttachments(SimpleServerEntity entity, List<Long> experimenterIds) {
        return webclientApi.deleteAttachments(entity, experimenterIds);
    }

    private CompletableFuture<List<URI>> getImageUrisOfProject(long projectId) {
        logger.debug("Finding image URIs contained in project with ID {}", projectId);

        return getDatasets(projectId, -1, -1).thenApply(datasets -> {
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

        return getImages(datasetId, -1, -1).thenApply(images -> {
            logger.debug("Found images {} belonging to dataset with ID {}. Now creating image URIs of them", images, datasetId);

            return images.stream()
                    .map(image -> new SimpleServerEntity(EntityType.IMAGE, image.getId()))
                    .map(this::getEntityUri)
                    .map(URI::create)
                    .distinct()
                    .toList();
        });
    }

    private CompletableFuture<List<URI>> getImageUrisOfScreen(long screenId) {
        logger.debug("Finding image URIs contained in screen with ID {}", screenId);

        return getPlates(screenId, -1, -1).thenApplyAsync(
                plates -> {
                    logger.debug("Found plates {} belonging to screen with ID {}. Now finding image URIs of them", plates, screenId);

                    return plates.stream()
                            .map(ServerEntity::getId)
                            .map(this::getImageUrisOfPlate)
                            .map(CompletableFuture::join)
                            .flatMap(List::stream)
                            .distinct()
                            .toList();
                },
                executorService
        );
    }

    private CompletableFuture<List<URI>> getImageUrisOfPlate(long plateId) {
        logger.debug("Finding image URIs contained in plate with ID {}", plateId);

        return getWellsFromPlate(plateId, -1, -1).thenApplyAsync(
                wells -> {
                    logger.debug("Found wells {} belonging to plate with ID {}. Now finding image URIs of them", wells, plateId);

                    return wells.stream()
                            .map(ServerEntity::getId)
                            .map(wellId -> getImageUrisOfWell(wellId, -1))
                            .map(CompletableFuture::join)
                            .flatMap(List::stream)
                            .distinct()
                            .toList();
                },
                executorService);
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
                    .mapToObj(i -> getWellsFromPlateAcquisition(plateAcquisitionId, -1, -1, i))
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

    private CompletableFuture<List<URI>> getImageUrisOfWell(long wellId, long plateAcquisitionOwnerId) {
        logger.debug("Finding image URIs contained in well with ID {} and belonging to plate acquisition with ID {}", wellId, plateAcquisitionOwnerId);

        return getWell(wellId).thenApply(well -> {
            logger.debug("Got well {}. Now getting image URIs of it", well);

            return well.getImageIds(plateAcquisitionOwnerId).stream()
                    .map(id -> new SimpleServerEntity(EntityType.IMAGE, id))
                    .map(this::getEntityUri)
                    .map(URI::create)
                    .distinct()
                    .toList();
        });
    }

    private static <T extends ServerEntity> T getEntityOfTypeInList(List<? extends ServerEntity> entities, Class<T> type) {
        return entities.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findAny()
                .orElse(null);
    }
}
