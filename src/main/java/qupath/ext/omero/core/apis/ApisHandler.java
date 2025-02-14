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
        long userId = jsonApi.getUserId();

        if (userId > -1) {
            return CompletableFuture.completedFuture(userId);
        } else {
            return webclientApi.getPublicUserId();
        }
    }

    /**
     * See {@link JsonApi#getDefaultGroup()}.
     */
    public Group getDefaultGroup() {
        return jsonApi.getDefaultGroup();
    }

    /**
     * See {@link JsonApi#getSessionUuid()}.
     */
    public String getSessionUuid() {
        return jsonApi.getSessionUuid();
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
            var matcher = pattern.matcher(URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8));

            if (matcher.find()) {
                String idValue = matcher.group(1);
                try {
                    long id = Long.parseLong(matcher.group(1));
                    logger.debug("Found ID {} in {}", id, uri);

                    return Optional.of(id);
                } catch (Exception e) {
                    logger.debug("Found entity ID {} but it is not an integer", idValue);
                }
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
     * @param entityURI the URI of the entity whose images should be retrieved. It can be URL encoded
     * @return a CompletableFuture (that may complete exceptionally) with the list described above
     */
    public CompletableFuture<List<URI>> getImagesURIFromEntityURI(URI entityURI) {
        logger.debug("Find image URIs indicated by {}", entityURI);

        String entityURL = URLDecoder.decode(entityURI.toString(), StandardCharsets.UTF_8);

        if (projectPattern.matcher(entityURL).find()) {
            logger.debug("{} refers to a project", entityURI);

            return parseEntityId(entityURI)
                    .map(this::getImagesURIOfProject)
                    .orElse(CompletableFuture.failedFuture(new IllegalArgumentException(
                            String.format("The provided URI %s was detected as a project but no ID was found", entityURL)
                    )));
        } else if (datasetPattern.matcher(entityURL).find()) {
            logger.debug("{} refers to a dataset", entityURI);

            return parseEntityId(entityURI)
                    .map(this::getImagesURIOfDataset)
                    .orElse(CompletableFuture.failedFuture(new IllegalArgumentException(
                            String.format("The provided URI %s was detected as a dataset but no ID was found", entityURL)
                    )));
        } else if (imagePatterns.stream().anyMatch(pattern -> pattern.matcher(entityURL).find())) {
            logger.debug("{} refers to an image", entityURI);

            return CompletableFuture.completedFuture(List.of(entityURI));
        } else {
            logger.debug("{} doesn't refer to a project, dataset, or image", entityURI);

            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    String.format("The provided URI %s does not represent a project, dataset, or image", entityURL)
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
     * Returns a list of image URIs contained in the dataset identified by the provided ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param datasetID the ID of the dataset the returned images must belong to
     * @return a CompletableFuture (that may complete exceptionally) with a list of URIs of images contained in the dataset
     */
    public CompletableFuture<List<URI>> getImagesURIOfDataset(long datasetID) {
        return getImages(datasetID).thenApply(images -> images.stream()
                .map(this::getItemURI)
                .map(URI::create)
                .toList()
        );
    }

    /**
     * Returns a list of image URIs contained in the project identified by the provided ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param projectID the ID of the project the returned images must belong to
     * @return a CompletableFuture (that may complete exceptionally) with a list of URIs of images contained in the project
     */
    public CompletableFuture<List<URI>> getImagesURIOfProject(long projectID) {
        return getDatasets(projectID).thenApplyAsync(datasets -> datasets.stream()
                .map(dataset -> getImagesURIOfDataset(dataset.getId()))
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList());
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
        if (orphanedImagesIds == null) {
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
    public CompletableFuture<List<Dataset>> getDatasets(long projectID) {
        return jsonApi.getDatasets(projectID);
    }

    /**
     * See {@link JsonApi#getImages(long)}.
     */
    public CompletableFuture<List<Image>> getImages(long datasetID) {
        return jsonApi.getImages(datasetID);
    }

    /**
     * See {@link JsonApi#getImage(long)}.
     */
    public CompletableFuture<Image> getImage(long imageID) {
        return jsonApi.getImage(imageID);
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
    public CompletableFuture<List<Plate>> getPlates(long screenID) {
        return jsonApi.getPlates(screenID);
    }

    /**
     * See {@link JsonApi#getPlateAcquisitions(long)}.
     */
    public CompletableFuture<List<PlateAcquisition>> getPlateAcquisitions(long plateID) {
        return jsonApi.getPlateAcquisitions(plateID);
    }

    /**
     * See {@link JsonApi#getWellsFromPlate(long)}.
     */
    public CompletableFuture<List<Well>> getWellsFromPlate(long plateID) {
        return jsonApi.getWellsFromPlate(plateID);
    }

    /**
     * See {@link JsonApi#getWellsFromPlateAcquisition(long,int)}.
     */
    public CompletableFuture<List<Well>> getWellsFromPlateAcquisition(long plateAcquisitionID, int wellSampleIndex) {
        return jsonApi.getWellsFromPlateAcquisition(plateAcquisitionID, wellSampleIndex);
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
     * See {@link WebclientApi#sendKeyValuePairs(long, Map, boolean, boolean)}.
     */
    public CompletableFuture<Void> sendKeyValuePairs(
            long imageId,
            Map<String, String> keyValues,
            boolean replaceExisting,
            boolean deleteExisting
    ) {
        return webclientApi.sendKeyValuePairs(imageId, keyValues, replaceExisting, deleteExisting);
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
        CompletableFuture<BufferedImage> request;

        try {
            request = omeroIconsCache.get(
                    type,
                    () -> {
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
                omeroIconsCache.invalidate(type);
            }
        });
    }

    /**
     * {@link #getThumbnail(long, int)} with a size of
     * {@link #THUMBNAIL_SIZE}.
     */
    public CompletableFuture<BufferedImage> getThumbnail(long id) {
        return getThumbnail(id, THUMBNAIL_SIZE);
    }

    /**
     * See {@link WebGatewayApi#getThumbnail(long, int)}.
     * Thumbnails are cached in a cache of size {@link #THUMBNAIL_CACHE_SIZE}.
     */
    public CompletableFuture<BufferedImage> getThumbnail(long id, int size) {
        IdSizeWrapper key = new IdSizeWrapper(id, size);

        CompletableFuture<BufferedImage> request;
        try {
            request = thumbnailsCache.get(
                    key,
                    () -> webGatewayApi.getThumbnail(id, size)
            );
        } catch (ExecutionException e) {
            return CompletableFuture.failedFuture(e);
        }

        return request.whenComplete((thumbnail, error) -> {
            if (thumbnail == null) {
                thumbnailsCache.invalidate(key);
            }
        });
    }

    /**
     * See {@link WebGatewayApi#getImageMetadata(long)}.
     * Metadata is cached in a cache of size {@link #METADATA_CACHE_SIZE}.
     */
    public CompletableFuture<ImageServerMetadata> getImageMetadata(long id) {
        CompletableFuture<ImageServerMetadata> request;
        try {
            request = metadataCache.get(id, () -> webGatewayApi.getImageMetadata(id));
        } catch (ExecutionException e) {
            return CompletableFuture.failedFuture(e);
        }

        return request.whenComplete((metadata, error) -> {
            if (metadata == null) {
                metadataCache.invalidate(id);
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
        return getImageSettings(imageID).thenCompose(imageSettings ->
                webGatewayApi.changeChannelColors(imageID, newChannelColors, imageSettings.getChannelSettings())
        );
    }

    /**
     * See {@link WebGatewayApi#changeChannelDisplayRanges(long, List, List)}.
     */
    public CompletableFuture<Void> changeChannelDisplayRanges(long imageID, List<ChannelSettings> newChannelSettings) {
        return getImageSettings(imageID).thenCompose(imageSettings ->
                webGatewayApi.changeChannelDisplayRanges(imageID, newChannelSettings, imageSettings.getChannelSettings())
        );
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
     * @param userId the ID of the user that should own the shapes to delete. Can be negative or equal to 0 to delete
     *               all shapes of the image
     * @return a void CompletableFuture (that completes exceptionally if the operation failed)
     */
    public CompletableFuture<Void> deleteShapes(long imageId, long userId) {
        return getShapes(imageId, userId).thenCompose(shapesToRemove -> iViewerApi.deleteShapes(imageId, shapesToRemove, jsonApi.getToken()));
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
     * See {@link WebclientApi#deleteAttachments(long, Class)}.
     */
    public CompletableFuture<Void> deleteAttachments(long entityId, Class<? extends RepositoryEntity> entityClass) {
        return webclientApi.deleteAttachments(entityId, entityClass);
    }
}
