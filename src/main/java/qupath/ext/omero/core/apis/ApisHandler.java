package qupath.ext.omero.core.apis;

import com.drew.lang.annotations.Nullable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.annotations.AnnotationGroup;
import qupath.ext.omero.core.entities.image.ChannelSettings;
import qupath.ext.omero.core.entities.image.ImageSettings;
import qupath.ext.omero.core.entities.login.LoginResponse;
import qupath.ext.omero.core.entities.repositoryentities.OrphanedFolder;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.*;
import qupath.ext.omero.core.entities.search.SearchQuery;
import qupath.ext.omero.core.entities.search.SearchResult;
import qupath.ext.omero.core.entities.shapes.Shape;
import qupath.ext.omero.core.WebUtilities;
import qupath.ext.omero.core.entities.imagemetadata.ImageMetadataResponse;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * <p>This class provides functions to perform operations with an OMERO server.</p>
 * <p>
 *     As different APIs are used to perform the operations, this class only
 *     redirects each web request to the appropriate API contained in this package.
 * </p>
 * <p>An instance of this class must be {@link #close() closed} once no longer used.</p>
 */
public class ApisHandler implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ApisHandler.class);
    private static final int THUMBNAIL_SIZE = 256;
    private static final int THUMBNAIL_CACHE_SIZE = 1000;
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
    private final URI host;
    private final JsonApi jsonApi;
    private final WebclientApi webclientApi;
    private final WebGatewayApi webGatewayApi;
    private final IViewerApi iViewerApi;
    private final BooleanProperty areOrphanedImagesLoading = new SimpleBooleanProperty(false);
    private final Cache<IdSizeWrapper, CompletableFuture<Optional<BufferedImage>>> thumbnailsCache = CacheBuilder.newBuilder()
            .maximumSize(THUMBNAIL_CACHE_SIZE)
            .build();
    private final Map<Class<? extends RepositoryEntity>, BufferedImage> omeroIconsCache = new ConcurrentHashMap<>();
    private final boolean canSkipAuthentication;
    private record IdSizeWrapper(long id, int size) {}

    private ApisHandler(URI host, JsonApi jsonApi, boolean canSkipAuthentication) {
        this.host = host;

        this.jsonApi = jsonApi;
        this.webclientApi = new WebclientApi(host, jsonApi.getToken());
        this.webGatewayApi = new WebGatewayApi(host, jsonApi.getToken());
        this.iViewerApi = new IViewerApi(host);

        this.canSkipAuthentication = canSkipAuthentication;
    }

    /**
     * <p>Attempt to create a request handler.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param host  the base server URI (e.g. <a href="https://idr.openmicroscopy.org">https://idr.openmicroscopy.org</a>)
     * @return a CompletableFuture with the request handler, an empty Optional if an error occurred
     */
    public static CompletableFuture<Optional<ApisHandler>> create(URI host) {
        return JsonApi.create(host).thenApplyAsync(jsonApi -> {
            if (jsonApi.isPresent()) {
                try {
                    return Optional.of(new ApisHandler(host, jsonApi.get(), jsonApi.get().canSkipAuthentication().get()));
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Error when creating APIs handler", e);
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        });
    }

    @Override
    public void close() throws Exception {
        webclientApi.close();
    }

    @Override
    public String toString() {
        return String.format("APIs handler of %s", host);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof ApisHandler apisHandler))
            return false;
        return Objects.equals(apisHandler.host, host);
    }

    @Override
    public int hashCode() {
        return host.hashCode();
    }

    /**
     * Convert a pixel type returned by OMERO to a QuPath {@link PixelType}
     *
     * @param pixelType  the OMERO pixel type
     * @return the QuPath pixel type, or an empty Optional if the OMERO pixel type was not recognized
     */
    public static Optional<PixelType> getPixelType(String pixelType) {
        return Optional.ofNullable(PIXEL_TYPE_MAP.get(pixelType));
    }

    /**
     * @return the web server URI (e.g. <a href="https://idr.openmicroscopy.org">https://idr.openmicroscopy.org</a>)
     */
    public URI getWebServerURI() {
        return host;
    }

    /**
     * See {@link JsonApi#getServerURI()}.
     */
    public String getServerURI() {
        return jsonApi.getServerURI();
    }

    /**
     * See {@link JsonApi#getServerPort()}.
     */
    public int getServerPort() {
        return jsonApi.getServerPort();
    }

    /**
     *
     * @return whether the server can be browsed without being authenticated
     */
    public boolean canSkipAuthentication() {
        return canSkipAuthentication;
    }

    /**
     * <p>Returns a list of image URIs contained in the dataset identified by the provided ID.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param datasetID  the ID of the dataset the returned images must belong to
     * @return a list of URIs of images contained in the dataset
     */
    public CompletableFuture<List<URI>> getImagesURIOfDataset(long datasetID) {
        return getImages(datasetID).thenApply(images -> images.stream()
                .map(this::getItemURI)
                .map(WebUtilities::createURI)
                .flatMap(Optional::stream)
                .toList()
        );
    }

    /**
     * <p>Returns a list of image URIs contained in the project identified by the provided ID.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param projectID  the ID of the project the returned images must belong to
     * @return a list of URIs of images contained in the project
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
     * See {@link JsonApi#login(String, String)}.
     */
    public CompletableFuture<LoginResponse> login(@Nullable String username, @Nullable String password) {
        return jsonApi.login(username, password);
    }

    /**
     * See {@link WebclientApi#ping()}.
     */
    public CompletableFuture<Boolean> ping() {
        return webclientApi.ping();
    }

    /**
     * See {@link WebclientApi#getOrphanedImagesIds()}.
     */
    public CompletableFuture<List<Long>> getOrphanedImagesIds() {
        return webclientApi.getOrphanedImagesIds();
    }

    /**
     * See {@link JsonApi#getGroups()} ()}.
     */
    public CompletableFuture<List<Group>> getGroups() {
        return jsonApi.getGroups();
    }

    /**
     * See {@link JsonApi#getOwners()} ()} ()}.
     */
    public CompletableFuture<List<Owner>> getOwners() {
        return jsonApi.getOwners();
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
    public CompletableFuture<Optional<Image>> getImage(long imageID) {
        return jsonApi.getImage(imageID);
    }

    /**
     * <p>Attempt to get the number of orphaned images of this server.</p>
     *
     * @return a CompletableFuture with the number of orphaned images, or 0 if it couldn't be retrieved
     */
    public CompletableFuture<Integer> getNumberOfOrphanedImages() {
        return getOrphanedImagesIds().thenApply(jsonApi::getNumberOfOrphanedImages);
    }

    /**
     * <p>
     *     Populate all orphaned images of this server to the list specified in parameter.
     *     This function populates and doesn't return a list because the number of images can
     *     be large, so this operation can take tens of seconds.
     * </p>
     * <p>The list can be updated from any thread.</p>
     *
     * @param children  the list which should be populated by the orphaned images. It should
     *                  be possible to add elements to this list
     */
    public void populateOrphanedImagesIntoList(List<Image> children) {
        setOrphanedImagesLoading(true);

        getOrphanedImagesIds()
                .thenAcceptAsync(orphanedImageIds -> jsonApi.populateOrphanedImagesIntoList(children, orphanedImageIds))
                .thenRun(() -> setOrphanedImagesLoading(false));
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
    public CompletableFuture<Optional<AnnotationGroup>> getAnnotations(
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
    public CompletableFuture<Boolean> sendKeyValuePairs(
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
    public CompletableFuture<Boolean> changeImageName(long imageId, String imageName) {
        return webclientApi.changeImageName(imageId, imageName);
    }

    /**
     * See {@link WebclientApi#changeChannelNames(long, List)}.
     */
    public CompletableFuture<Boolean> changeChannelNames(long imageId, List<String> channelsName) {
        return webclientApi.changeChannelNames(imageId, channelsName);
    }

    /**
     * See {@link WebclientApi#sendAttachment(long, Class, String, String)}.
     */
    public CompletableFuture<Boolean> sendAttachment(
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
    public CompletableFuture<Boolean> deleteAttachments(long entityId, Class<? extends RepositoryEntity> entityClass) {
        return webclientApi.deleteAttachments(entityId, entityClass);
    }

    /**
     * <p>Attempt to retrieve the icon of an OMERO entity.</p>
     * <p>Icons for orphaned folders, projects, datasets, images, screens, plates, and plate acquisitions can be retrieved.</p>
     * <p>Icons are cached.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param type  the class of the entity whose icon is to be retrieved
     * @return a CompletableFuture with the icon if the operation succeeded, or an empty Optional otherwise
     */
    public CompletableFuture<Optional<BufferedImage>> getOmeroIcon(Class<? extends RepositoryEntity> type) {
        if (omeroIconsCache.containsKey(type)) {
            return CompletableFuture.completedFuture(Optional.of(omeroIconsCache.get(type)));
        } else {
            if (type.equals(Project.class)) {
                return webGatewayApi.getProjectIcon().thenApply(icon -> {
                    icon.ifPresent(bufferedImage -> omeroIconsCache.put(type, bufferedImage));
                    return icon;
                });
            } else if (type.equals(Dataset.class)) {
                return webGatewayApi.getDatasetIcon().thenApply(icon -> {
                    icon.ifPresent(bufferedImage -> omeroIconsCache.put(type, bufferedImage));
                    return icon;
                });
            } else if (type.equals(OrphanedFolder.class)) {
                return webGatewayApi.getOrphanedFolderIcon().thenApply(icon -> {
                    icon.ifPresent(bufferedImage -> omeroIconsCache.put(type, bufferedImage));
                    return icon;
                });
            } else if (type.equals(Image.class)) {
                return webclientApi.getImageIcon().thenApply(icon -> {
                    icon.ifPresent(bufferedImage -> omeroIconsCache.put(type, bufferedImage));
                    return icon;
                });
            } else if (type.equals(Screen.class)) {
                return webclientApi.getScreenIcon().thenApply(icon -> {
                    icon.ifPresent(bufferedImage -> omeroIconsCache.put(type, bufferedImage));
                    return icon;
                });
            } else if (type.equals(Plate.class)) {
                return webclientApi.getPlateIcon().thenApply(icon -> {
                    icon.ifPresent(bufferedImage -> omeroIconsCache.put(type, bufferedImage));
                    return icon;
                });
            } else if (type.equals(PlateAcquisition.class)) {
                return webclientApi.getPlateAcquisitionIcon().thenApply(icon -> {
                    icon.ifPresent(bufferedImage -> omeroIconsCache.put(type, bufferedImage));
                    return icon;
                });
            } else {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        }
    }

    /**
     * {@link #getThumbnail(long, int)} with a size of
     * {@link #THUMBNAIL_SIZE}.
     */
    public CompletableFuture<Optional<BufferedImage>> getThumbnail(long id) {
        return getThumbnail(id, THUMBNAIL_SIZE);
    }

    /**
     * See {@link WebGatewayApi#getThumbnail(long, int)}.
     * Thumbnails are cached in a cache of size {@link #THUMBNAIL_CACHE_SIZE}.
     */
    public CompletableFuture<Optional<BufferedImage>> getThumbnail(long id, int size) {
        try {
            IdSizeWrapper key = new IdSizeWrapper(id, size);
            CompletableFuture<Optional<BufferedImage>> request = thumbnailsCache.get(
                    key,
                    () -> webGatewayApi.getThumbnail(id, size)
            );

            request.thenAccept(response -> {
                if (response.isEmpty()) {
                    thumbnailsCache.invalidate(key);
                }
            });
            return request;
        } catch (ExecutionException e) {
            logger.error("Error when retrieving thumbnail", e);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /**
     * See {@link WebGatewayApi#getImageMetadata(long)}.
     */
    public CompletableFuture<Optional<ImageMetadataResponse>> getImageMetadata(long id) {
        return webGatewayApi.getImageMetadata(id);
    }

    /**
     * See {@link WebGatewayApi#readTile(long, TileRequest, int, int, double)}.
     */
    public CompletableFuture<Optional<BufferedImage>> readTile(
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
    public CompletableFuture<Boolean> changeChannelColors(long imageID, List<Integer> newChannelColors) {
        return getImageSettings(imageID).thenCompose(imageSettings -> {
            if (imageSettings.isPresent()) {
                return webGatewayApi.changeChannelColors(imageID, newChannelColors, imageSettings.get().getChannelSettings());
            } else {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    /**
     * See {@link WebGatewayApi#changeChannelDisplayRanges(long, List, List)}.
     */
    public CompletableFuture<Boolean> changeChannelDisplayRanges(long imageID, List<ChannelSettings> newChannelSettings) {
        return getImageSettings(imageID).thenCompose(imageSettings -> {
            if (imageSettings.isPresent()) {
                return webGatewayApi.changeChannelDisplayRanges(imageID, newChannelSettings, imageSettings.get().getChannelSettings());
            } else {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    /**
     * See {@link JsonApi#getROIs(long)}.
     */
    public CompletableFuture<List<Shape>> getROIs(long id) {
        return jsonApi.getROIs(id);
    }

    /**
     * See {@link IViewerApi#writeROIs(long, Collection, Collection, String)}.
     */
    public CompletableFuture<Boolean> writeROIs(long id, Collection<Shape> shapes, boolean removeExistingROIs) {
        CompletableFuture<List<Shape>> roisToRemoveFuture = removeExistingROIs ? getROIs(id) : CompletableFuture.completedFuture(List.of());

        return roisToRemoveFuture.thenCompose(roisToRemove -> iViewerApi.writeROIs(id, shapes, roisToRemove, jsonApi.getToken()));
    }

    /**
     * See {@link IViewerApi#getImageSettings(long)}.
     */
    public CompletableFuture<Optional<ImageSettings>> getImageSettings(long imageId) {
        return iViewerApi.getImageSettings(imageId);
    }

    private synchronized void setOrphanedImagesLoading(boolean orphanedImagesLoading) {
        areOrphanedImagesLoading.set(orphanedImagesLoading);
    }
}
