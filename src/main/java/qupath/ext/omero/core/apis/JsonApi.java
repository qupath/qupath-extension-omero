package qupath.ext.omero.core.apis;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableIntegerValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.entities.LoginResponse;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions2.Experimenter;
import qupath.ext.omero.core.entities.permissions2.ExperimenterGroup;
import qupath.ext.omero.core.entities.permissions2.omeroentities.OmeroExperimenter;
import qupath.ext.omero.core.entities.permissions2.omeroentities.OmeroExperimenterGroup;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Image;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.PlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.ServerEntity;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Well;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroDataset;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroPlate;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroPlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroProject;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroScreen;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroWell;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.image.OmeroImage;
import qupath.ext.omero.core.entities.serverinformation.Links;
import qupath.ext.omero.core.entities.serverinformation.OmeroServer;
import qupath.ext.omero.core.entities.serverinformation.OmeroServers;
import qupath.ext.omero.core.entities.serverinformation.SupportedVersion;
import qupath.ext.omero.core.entities.serverinformation.SupportedVersions;
import qupath.ext.omero.core.entities.serverinformation.Token;
import qupath.ext.omero.core.entities.shapes.Shape;
import qupath.lib.io.GsonTools;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * The OMERO <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html">JSON API</a>.
 * <p>
 * This API is used to get basic information on the server, authenticate, get details on OMERO entities
 * (e.g. images, datasets), and get ROIs of an image.
 */
class JsonApi {

    private static final Logger logger = LoggerFactory.getLogger(JsonApi.class);
    private static final int SERVER_ENTITIES_IDS_CACHE_SIZE = 10000;
    private static final int SERVER_ENTITIES_CACHE_SIZE = 1000;
    private static final String EXPECTED_API_VERSION = "0";
    private static final String API_URL = "%s/api/";
    private static final String GROUPS_OF_USER_URL = "%s%d/experimentergroups/";
    private static final String PROJECTS_URL = "%s?childCount=true&owner=%d&group=%d";
    private static final String DATASETS_IN_PROJECT_URL = "%s%d/datasets/?childCount=true&owner=%d&group=%d";
    private static final String ORPHANED_DATASETS_URL = "%s?childCount=true&orphaned=true&owner=%d&group=%d";
    private static final String IMAGES_IN_DATASET_URL = "%s%d/images/?childCount=true&owner=%d&group=%d";
    private static final String ORPHANED_IMAGES_URL = "%s?childCount=true&orphaned=true&owner=%d&group=%d";
    private static final String SCREENS_URL = "%s?childCount=true&owner=%d&group=%d";
    private static final String PLATES_IN_SCREEN_URL = "%s%d/plates/?owner=%d&group=%d";    //TODO: don't need childCount=true?
    private static final String ORPHANED_PLATES_URL = "%s?orphaned=true&owner=%d&group=%d";    //TODO: don't need childCount=true?
    private static final String PLATE_ACQUISITION_URL = "%s/api/v0/m/plateacquisitions/%d";
    private static final String PLATE_ACQUISITIONS_IN_PLATE_URL = "%s/api/v0/m/plates/%d/plateacquisitions/?owner=%d&group=%d";  //TODO: don't need childCount=true?
    private static final String WELLS_URL = "%s/api/v0/m/wells/%d";
    private static final String WELLS_IN_PLATE_URL = "%s/api/v0/m/plates/%d/wells/?owner=%d&group=%d"; //TODO: don't need childCount=true?
    private static final String WELLS_IN_PLATE_ACQUISITION_URL = "%s/api/v0/m/plateacquisitions/%d/wellsampleindex/%d/wells/?owner=%d&group=%d"; //TODO: don't need childCount=true?
    private static final String ROIS_URL = "%s/api/v0/m/rois/?image=%d%s";
    private static final List<String> GROUPS_TO_EXCLUDE = List.of("system", "user");
    private static final Gson gson = new Gson();
    private final Cache<ServerEntityCacheKey, List<Long>> projectIdsCache = CacheBuilder.newBuilder()
            .maximumSize(SERVER_ENTITIES_IDS_CACHE_SIZE)
            .build();
    private final Cache<ServerEntityCacheKey, List<Long>> datasetIdsCache = CacheBuilder.newBuilder()
            .maximumSize(SERVER_ENTITIES_IDS_CACHE_SIZE)
            .build();
    private final Cache<ServerEntityCacheKey, List<Long>> imageIdsCache = CacheBuilder.newBuilder()
            .maximumSize(SERVER_ENTITIES_IDS_CACHE_SIZE)
            .build();
    private final Cache<ServerEntityCacheKey, List<Long>> screenIdsCache = CacheBuilder.newBuilder()
            .maximumSize(SERVER_ENTITIES_IDS_CACHE_SIZE)
            .build();
    private final Cache<ServerEntityCacheKey, List<Long>> plateIdsCache = CacheBuilder.newBuilder()
            .maximumSize(SERVER_ENTITIES_IDS_CACHE_SIZE)
            .build();
    private final Cache<ServerEntityCacheKey, List<Long>> plateAcquisitionIdsCache = CacheBuilder.newBuilder()
            .maximumSize(SERVER_ENTITIES_IDS_CACHE_SIZE)
            .build();
    private final Cache<ServerEntityCacheKey, List<Long>> wellIdsCache = CacheBuilder.newBuilder()
            .maximumSize(SERVER_ENTITIES_IDS_CACHE_SIZE)
            .build();
    private final IntegerProperty numberOfEntitiesLoading = new SimpleIntegerProperty(0);
    private final URI webServerUri;
    private final RequestSender requestSender;
    private final Links links;
    private final OmeroServer omeroServer;
    private final String token;
    private final LoginResponse loginResponse;
    private final LoadingCache<Long, Project> projectsCache;
    private final LoadingCache<Long, Dataset> datasetsCache;
    private final LoadingCache<Long, Image> imagesCache;
    private final LoadingCache<Long, Screen> screensCache;
    private final LoadingCache<Long, Plate> platesCache;
    private final LoadingCache<Long, PlateAcquisition> plateAcquisitionsCache;
    private final LoadingCache<Long, Well> wellsCache;
    private record ServerEntityCacheKey(long parentId, long ownerId, long groupId) {}

    //TODO: handle https://omero.readthedocs.io/en/stable/developers/json-api.html#normalizing-experimenters-and-groups
    //TODO: handle when owner / group is -1 (+ javadoc)
    /**
     * Create a JSON API client. This will send a few requests to get basic information on the server and
     * authenticate if necessary, so it can take a few seconds. However, this operation is cancellable.
     *
     * @param webServerUri the URL to the OMERO web server to connect to
     * @param requestSender the request sender to use when sending requests
     * @param credentials the credentials to use for the authentication
     * @throws URISyntaxException if a link to the server cannot be created
     * @throws ExecutionException if a request to the server fails or if a response does not contain expected elements.
     * This can happen if the server is unreachable or if the authentication fails for example
     * @throws InterruptedException if the running thread is interrupted
     * @throws IllegalArgumentException if the server doesn't return all necessary information
     */
    public JsonApi(URI webServerUri, RequestSender requestSender, Credentials credentials) throws URISyntaxException, ExecutionException, InterruptedException {
        this.webServerUri = webServerUri;
        this.requestSender = requestSender;

        this.links = getLinks(requestSender, getLinksUrl(requestSender, webServerUri));
        logger.debug("Got links {} from {}", links, webServerUri);

        // Send requests in parallel
        CompletableFuture<String> tokenRequest = getToken(requestSender, this.links);
        CompletableFuture<OmeroServer> omeroServersRequest = getOmeroServer(requestSender, this.links);

        this.omeroServer = omeroServersRequest.get();
        logger.debug("Got OMERO server {} from {}", omeroServer, webServerUri);

        this.token = tokenRequest.get();
        logger.debug("Got token {} from {}", token, webServerUri);

        if (credentials.userType().equals(Credentials.UserType.REGULAR_USER)) {
            this.loginResponse = login(this.requestSender, credentials, links.login(), omeroServer.id(), token).get();

            logger.debug(
                    "Created JSON API with authenticated {} user of ID {} and default group {}",
                    loginResponse.isAdmin() ? "admin" : "non admin",
                    loginResponse.userId(),
                    loginResponse.group()
            );
        } else {
            this.loginResponse = null;
            logger.debug("Created JSON API with unauthenticated user");
        }

        this.projectsCache = createCache(
                projectId -> String.format("%s%d", links.projects(), projectId),
                jsonElement -> new Project(gson.fromJson(jsonElement, OmeroProject.class), webServerUri)
        );
        this.datasetsCache = createCache(
                datasetId -> String.format("%s%d", links.datasets(), datasetId),
                jsonElement -> new Dataset(gson.fromJson(jsonElement, OmeroDataset.class), webServerUri)
        );
        this.imagesCache = createCache(
                imageId -> String.format("%s%d", links.images(), imageId),
                jsonElement -> new Image(gson.fromJson(jsonElement, OmeroImage.class), webServerUri)
        );
        this.screensCache = createCache(
                screenId -> String.format("%s%d", links.screens(), screenId),
                jsonElement -> new Screen(gson.fromJson(jsonElement, OmeroScreen.class), webServerUri)
        );
        this.platesCache = createCache(
                plateId -> String.format("%s%d", links.plates(), plateId),
                jsonElement -> new Plate(gson.fromJson(jsonElement, OmeroPlate.class), webServerUri)
        );
        this.plateAcquisitionsCache = createCache(
                plateAcquisitionId -> String.format(PLATE_ACQUISITION_URL, webServerUri, plateAcquisitionId),
                jsonElement -> new PlateAcquisition(gson.fromJson(jsonElement, OmeroPlateAcquisition.class), -1, webServerUri)
        );
        this.wellsCache = createCache(
                wellId -> String.format(WELLS_URL, webServerUri, wellId),
                jsonElement -> new Well(gson.fromJson(jsonElement, OmeroWell.class), -1, webServerUri)
        );
    }

    @Override
    public String toString() {
        return String.format("JSON API of %s", webServerUri);
    }

    /**
     * @return the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a> used by this session
     */
    public String getToken() {
        return token;
    }

    /**
     * Get the server URI of this server. This is the <b>OMERO server</b>
     * URI and may be different from the <b>OMERO web</b> URI.
     * <p>
     * The returned address is the address used by OMERO web to communicate
     * the returned value of this function may be {@code localhost} or any local IP.
     * Therefore, if you can't communicate with the returned value of this function,
     * you should be able to communicate with the address of OMERO web (returned by
     * {@link ApisHandler#getWebServerURI()}.
     *
     * @return the server host
     */
    public String getServerUri() {
        return omeroServer.host();
    }

    /**
     * @return the server port of this server. This is the OMERO server
     * port and may be different from the OMERO web port
     */
    public int getServerPort() {
        return omeroServer.port();
    }

    /**
     * @return the user ID of the connected user, or an empty Optional if no authentication was performed
     */
    public Optional<Long> getUserId() {
        return Optional.ofNullable(loginResponse).map(LoginResponse::userId);
    }

    /**
     * @return the default group of the connected user, or an empty Optional if no authentication was performed
     */
    public Optional<Group> getDefaultGroup() {
        return Optional.ofNullable(loginResponse).map(LoginResponse::group);
    }

    /**
     * @return the session UUID of the current connection, or an empty Optional if no authentication was performed
     */
    public Optional<String> getSessionUuid() {
        return Optional.ofNullable(loginResponse).map(LoginResponse::sessionUuid);
    }

    /**
     * @return whether the connected user is an administrator, or an empty Optional if no authentication was performed
     */
    public Optional<Boolean> isAdmin() {
        return Optional.ofNullable(loginResponse).map(LoginResponse::isAdmin);
    }

    /**
     * Indicate whether the connected user is an owner (or leader) of the group with the provided ID.
     * If no authentication was performed, false is returned.
     *
     * @param groupId the ID of the group to check ownership
     * @return whether the connected user is an owner of the provided group
     */
    public boolean isConnectedUserOwnerOfGroup(long groupId) {
        return loginResponse != null && loginResponse.ownedGroupIds().contains(groupId);
    }

    /**
     * @return the number of OMERO entities (e.g. datasets, images) currently being loaded by the API.
     * This property may be updated from any thread
     */
    public ObservableIntegerValue getNumberOfEntitiesLoading() {
        return numberOfEntitiesLoading;
    }

    /**
     * Attempt to retrieve all experimenter groups of a user or of the server.
     * <ul>
     *     <li>
     *         When retrieving the groups of a user, private groups won't be populated by their members (excluding the provided user),
     *         unless the provided user is the connected user and this user is an owner of the group.
     *         Also, the 'system' and 'user' groups won't be included.
     *     </li>
     *     <li>When retrieving all groups of the server, private groups will be populated by all their members.</li>
     * </ul>
     *
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if a request failed for example).
     *
     * @param userId the ID of the user that belong to the returned groups. Can be negative to retrieve all groups
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all groups of the provided user
     */
    public CompletableFuture<List<ExperimenterGroup>> getGroups(long userId) {
        boolean retrieveAllGroups = userId < 0;

        if (retrieveAllGroups) {
            logger.debug("Getting all groups of the server");
        } else {
            logger.debug("Getting groups of user with ID {}", userId);
        }

        URI uri;
        try {
            uri = new URI(retrieveAllGroups ? links.groups() : String.format(GROUPS_OF_USER_URL, links.owners(), userId));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.getPaginated(uri).thenApplyAsync(jsonElements -> jsonElements.stream()
                .map(jsonElement -> gson.fromJson(jsonElement, OmeroExperimenterGroup.class))
                .filter(OmeroExperimenterGroup::idNameAndExperimenterUrlDefined)
                .filter(omeroGroup -> retrieveAllGroups || !GROUPS_TO_EXCLUDE.contains(omeroGroup.name()))
                .map(omeroGroup -> {
                    try {
                        return new ExperimenterGroup(
                                omeroGroup,
                                requestSender.getPaginated(new URI(omeroGroup.experimentersUrl())).get().stream()
                                        .map(jsonElement -> new Experimenter(gson.fromJson(jsonElement, OmeroExperimenter.class)))
                                        .filter(experimenter -> {
                                            if (retrieveAllGroups) {
                                                return true;
                                            } else if (loginResponse != null && userId == loginResponse.userId()) {
                                                return switch (omeroGroup.getPermissionLevel()) {
                                                    case PRIVATE -> experimenter.getId() == userId || loginResponse.ownedGroupIds().contains(omeroGroup.id());
                                                    case READ_ONLY, READ_ANNOTATE, READ_WRITE -> true;
                                                    case UNKNOWN -> experimenter.getId() == userId;
                                                };
                                            } else {
                                                return switch (omeroGroup.getPermissionLevel()) {
                                                    case PRIVATE, UNKNOWN -> experimenter.getId() == userId;
                                                    case READ_ONLY, READ_ANNOTATE, READ_WRITE -> true;
                                                };
                                            }
                                        })
                                        .toList()
                        );
                    } catch (ExecutionException | InterruptedException | URISyntaxException | IllegalArgumentException e) {
                        logger.error("Cannot create experimenter group corresponding to {}. Skipping it", omeroGroup, e);

                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }

                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList()
        );
    }

    /**
     * Attempt to retrieve projects belonging to the provided owner and group and visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param ownerId the ID of the owner that should own the projects
     * @param groupId the ID of the group that should own the projects
     * @return a CompletableFuture (that may complete exceptionally) with all projects belonging to the provided owner and group
     * and visible by the current user
     */
    public CompletableFuture<List<Project>> getProjects(long ownerId, long groupId) {
        logger.debug("Getting projects belonging to owner with ID {} and group with ID {}", ownerId, groupId);

        return getChildren(
                projectIdsCache,
                projectsCache,
                new ServerEntityCacheKey(-1, ownerId, groupId),
                String.format(PROJECTS_URL, links.projects(), ownerId, groupId),
                jsonElement -> new Project(gson.fromJson(jsonElement, OmeroProject.class), webServerUri)
        );
    }

    /**
     * Attempt to retrieve a project from a project ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param projectId the ID of the project to get
     * @return a CompletableFuture (that may complete exceptionally) with the project
     */
    public CompletableFuture<Project> getProject(long projectId) {
        logger.debug("Getting project with ID {}", projectId);

        return CompletableFuture.supplyAsync(() -> projectsCache.getUnchecked(projectId));
    }

    /**
     * Attempt to retrieve all datasets belonging to the provided project, owner and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param projectId the ID of the project whose datasets should be retrieved
     * @param ownerId the ID of the owner that should own the datasets
     * @param groupId the ID of the group that should own the datasets
     * @return a CompletableFuture (that may complete exceptionally) with all datasets belonging to the provided
     * project, owner and group
     */
    public CompletableFuture<List<Dataset>> getDatasets(long projectId, long ownerId, long groupId) {
        logger.debug("Getting datasets belonging to project with ID {}, owner with ID {} and group with ID {}", projectId, ownerId, groupId);

        return getChildren(
                datasetIdsCache,
                datasetsCache,
                new ServerEntityCacheKey(projectId, ownerId, groupId),
                String.format(DATASETS_IN_PROJECT_URL, links.projects(), projectId, ownerId, groupId),
                jsonElement -> new Dataset(gson.fromJson(jsonElement, OmeroDataset.class), webServerUri)
        );
    }

    /**
     * Attempt to retrieve all orphaned datasets belonging to the provided owner and group and visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param ownerId the ID of the owner that should own the datasets
     * @param groupId the ID of the group that should own the datasets
     * @return a CompletableFuture (that may complete exceptionally) with all orphaned datasets belonging to the provided
     * owner and group
     */
    public CompletableFuture<List<Dataset>> getOrphanedDatasets(long ownerId, long groupId) {
        logger.debug("Getting orphaned datasets belonging to owner with ID {} and group with ID {}", ownerId, groupId);

        return getChildren(
                datasetIdsCache,
                datasetsCache,
                new ServerEntityCacheKey(-1, ownerId, groupId),
                String.format(ORPHANED_DATASETS_URL, links.projects(), ownerId, groupId),
                jsonElement -> new Dataset(gson.fromJson(jsonElement, OmeroDataset.class), webServerUri)
        );
    }

    /**
     * Attempt to retrieve a dataset from a dataset ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param datasetId the ID of the dataset to get
     * @return a CompletableFuture (that may complete exceptionally) with the dataset
     */
    public CompletableFuture<Dataset> getDataset(long datasetId) {
        logger.debug("Getting dataset with ID {}", datasetId);

        return CompletableFuture.supplyAsync(() -> datasetsCache.getUnchecked(datasetId));
    }

    /**
     * Attempt to retrieve all images belonging to the provided dataset, owner and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param datasetId the ID of the dataset whose images should be retrieved
     * @param ownerId the ID of the owner that should own the images
     * @param groupId the ID of the group that should own the images
     * @return a CompletableFuture (that may complete exceptionally) with all images belonging to the provided
     * dataset, owner and group
     */
    public CompletableFuture<List<Image>> getImages(long datasetId, long ownerId, long groupId) {
        logger.debug("Getting images belonging to dataset with ID {}, owner with ID {} and group with ID {}", datasetId, ownerId, groupId);

        return getChildren(
                imageIdsCache,
                imagesCache,
                new ServerEntityCacheKey(datasetId, ownerId, groupId),
                String.format(IMAGES_IN_DATASET_URL, links.datasets(), datasetId, ownerId, groupId),
                jsonElement -> new Image(gson.fromJson(jsonElement, OmeroImage.class), webServerUri)
        );
    }

    /**
     * Attempt to retrieve all orphaned images belonging to the provided owner and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param ownerId the ID of the owner that should own the images
     * @param groupId the ID of the group that should own the images
     * @return a CompletableFuture (that may complete exceptionally) with all orphaned images belonging to the provided
     * owner and group
     */
    public CompletableFuture<List<Image>> getOrphanedImages(long ownerId, long groupId) {
        logger.debug("Getting orphaned images belonging to owner with ID {} and group with ID {}", ownerId, groupId);

        return getChildren(
                imageIdsCache,
                imagesCache,
                new ServerEntityCacheKey(-1, ownerId, groupId),
                String.format(ORPHANED_IMAGES_URL, links.images(), ownerId, groupId),
                jsonElement -> new Image(gson.fromJson(jsonElement, OmeroImage.class), webServerUri)
        );
    }

    /**
     * Attempt to retrieve an image from an image ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param imageId the ID of the image to get
     * @return a CompletableFuture (that may complete exceptionally) with the image
     */
    public CompletableFuture<Image> getImage(long imageId) {
        logger.debug("Getting image with ID {}", imageId);

        return CompletableFuture.supplyAsync(() -> imagesCache.getUnchecked(imageId));
    }

    /**
     * Attempt to retrieve screens belonging to the provided owner and group and visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param ownerId the ID of the owner that should own the screens
     * @param groupId the ID of the group that should own the screens
     * @return a CompletableFuture (that may complete exceptionally) with all screens belonging to the provided owner and group
     * and visible by the current user
     */
    public CompletableFuture<List<Screen>> getScreens(long ownerId, long groupId) {
        logger.debug("Getting screens belonging to owner with ID {} and group with ID {}", ownerId, groupId);

        return getChildren(
                screenIdsCache,
                screensCache,
                new ServerEntityCacheKey(-1, ownerId, groupId),
                String.format(SCREENS_URL, links.screens(), ownerId, groupId),
                jsonElement -> new Screen(gson.fromJson(jsonElement, OmeroScreen.class), webServerUri)
        );
    }

    /**
     * Attempt to retrieve a screen from a screen ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param screenId the ID of the screen to get
     * @return a CompletableFuture (that may complete exceptionally) with the screen
     */
    public CompletableFuture<Screen> getScreen(long screenId) {
        logger.debug("Getting screen with ID {}", screenId);

        return CompletableFuture.supplyAsync(() -> screensCache.getUnchecked(screenId));
    }

    /**
     * Attempt to retrieve all plates belonging to the provided screen, owner and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param screenId the ID of the screen whose plates should be retrieved
     * @param ownerId the ID of the owner that should own the plates
     * @param groupId the ID of the group that should own the plates
     * @return a CompletableFuture (that may complete exceptionally) with all plates belonging to the provided
     * screen, owner and group
     */
    public CompletableFuture<List<Plate>> getPlates(long screenId, long ownerId, long groupId) {
        logger.debug("Getting plates belonging to screen with ID {}, owner with ID {} and group with ID {}", screenId, ownerId, groupId);

        return getChildren(
                plateIdsCache,
                platesCache,
                new ServerEntityCacheKey(screenId, ownerId, groupId),
                String.format(PLATES_IN_SCREEN_URL, links.screens(), screenId, ownerId, groupId),
                jsonElement -> new Plate(gson.fromJson(jsonElement, OmeroPlate.class), webServerUri)
        );
    }

    /**
     * Attempt to retrieve all orphaned plates belonging to the provided owner and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param ownerId the ID of the owner that should own the plates
     * @param groupId the ID of the group that should own the plates
     * @return a CompletableFuture (that may complete exceptionally) with all orphaned plates belonging to the provided
     * owner and group
     */
    public CompletableFuture<List<Plate>> getOrphanedPlates(long ownerId, long groupId) {
        logger.debug("Getting orphaned plates belonging to owner with ID {} and group with ID {}", ownerId, groupId);

        return getChildren(
                plateIdsCache,
                platesCache,
                new ServerEntityCacheKey(-1, ownerId, groupId),
                String.format(ORPHANED_PLATES_URL, links.plates(), ownerId, groupId),
                jsonElement -> new Plate(gson.fromJson(jsonElement, OmeroPlate.class), webServerUri)
        );
    }

    /**
     * Attempt to retrieve a plate from a plate ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateId the ID of the plate to get
     * @return a CompletableFuture (that may complete exceptionally) with the plate
     */
    public CompletableFuture<Plate> getPlate(long plateId) {
        logger.debug("Getting plate with ID {}", plateId);

        return CompletableFuture.supplyAsync(() -> platesCache.getUnchecked(plateId));
    }

    /**
     * Attempt to retrieve all plate acquisitions belonging to the provided plate, owner and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateId the ID of the plate whose plate acquisitions should be retrieved
     * @param ownerId the ID of the owner that should own the plate acquisitions
     * @param groupId the ID of the group that should own the plate acquisitions
     * @param numberOfWells the number of wells each returned plate acquisition should have
     * @return a CompletableFuture (that may complete exceptionally) with all plate acquisitions belonging to the provided
     * plate, owner and group
     */
    public CompletableFuture<List<PlateAcquisition>> getPlateAcquisitions(long plateId, long ownerId, long groupId, int numberOfWells) {
        logger.debug("Getting plate acquisitions belonging to plate with ID {}, owner with ID {} and group with ID {}", plateId, ownerId, groupId);

        return getChildren(
                plateAcquisitionIdsCache,
                plateAcquisitionsCache,
                new ServerEntityCacheKey(plateId, ownerId, groupId),
                String.format(PLATE_ACQUISITIONS_IN_PLATE_URL, webServerUri, plateId, ownerId, groupId),
                jsonElement -> new PlateAcquisition(
                        gson.fromJson(jsonElement, OmeroPlateAcquisition.class),
                        numberOfWells,
                        webServerUri
                )
        );
    }

    /**
     * Attempt to retrieve a plate acquisition from a plate acquisition ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateAcquisitionId the ID of the plate acquisition to get
     * @return a CompletableFuture (that may complete exceptionally) with the plate acquisition
     */
    public CompletableFuture<PlateAcquisition> getPlateAcquisition(long plateAcquisitionId) {
        logger.debug("Getting plate acquisition with ID {}", plateAcquisitionId);

        return CompletableFuture.supplyAsync(() -> plateAcquisitionsCache.getUnchecked(plateAcquisitionId));
    }

    /**
     * Attempt to retrieve all wells belonging to the provided plate, owner and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateId the ID of the plate whose wells should be retrieved
     * @param ownerId the ID of the owner that should own the wells
     * @param groupId the ID of the group that should own the wells
     * @return a CompletableFuture (that may complete exceptionally) with all wells belonging to the provided
     * plate, owner and group
     */
    public CompletableFuture<List<Well>> getWellsFromPlate(long plateId, long ownerId, long groupId) {
        logger.debug("Getting wells belonging to plate with ID {}, owner with ID {} and group with ID {}", plateId, ownerId, groupId);

        return getChildren(
                wellIdsCache,
                wellsCache,
                new ServerEntityCacheKey(plateId, ownerId, groupId),
                String.format(WELLS_IN_PLATE_URL, webServerUri, plateId, ownerId, groupId),
                jsonElement -> new Well(
                        gson.fromJson(jsonElement, OmeroWell.class),
                        -1,
                        webServerUri
                )
        );
    }

    /**
     * Attempt to retrieve all wells belonging to the provided plate acquisition, owner and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateAcquisitionId the ID of the plate acquisition whose wells should be retrieved
     * @param ownerId the ID of the owner that should own the wells
     * @param groupId the ID of the group that should own the wells
     * @param wellSampleIndex the index of the well sample owning the wells to retrieve
     * @return a CompletableFuture (that may complete exceptionally) with all wells belonging to the provided
     * plate acquisition, owner and group
     */
    public CompletableFuture<List<Well>> getWellsFromPlateAcquisition(long plateAcquisitionId, long ownerId, long groupId, int wellSampleIndex) {
        logger.debug(
                "Getting wells belonging to plate acquisition with ID {}, owner with ID {}, group with ID {} and well sample index {}",
                plateAcquisitionId,
                ownerId,
                groupId,
                wellSampleIndex
        );

        return getChildren(
                wellIdsCache,
                wellsCache,
                new ServerEntityCacheKey(-plateAcquisitionId, ownerId, groupId),        // use negative ID to use same cache
                String.format(WELLS_IN_PLATE_ACQUISITION_URL, webServerUri, plateAcquisitionId, wellSampleIndex, ownerId, groupId),
                jsonElement -> new Well(
                        gson.fromJson(jsonElement, OmeroWell.class),
                        plateAcquisitionId,
                        webServerUri
                )
        );
    }

    /**
     * Attempt to retrieve a well from a well ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param wellId the ID of the well to get
     * @return a CompletableFuture (that may complete exceptionally) with the well
     */
    public CompletableFuture<Well> getWell(long wellId) {
        logger.debug("Getting well with ID {}", wellId);

        return CompletableFuture.supplyAsync(() -> wellsCache.getUnchecked(wellId));
    }

    /**
     * Attempt to retrieve shapes of an image optionally belonging to a user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request or the conversion failed for example).
     *
     * @param imageId the OMERO image ID
     * @param userId the ID of the user that should own the shapes to retrieve. Can be negative or equal to 0 to get
     *               all shapes of the image
     * @return a CompletableFuture (that may complete exceptionally) with the list of ROIs, or an empty list if no ROIs
     * was found with the provided ID
     */
    public CompletableFuture<List<Shape>> getShapes(long imageId, long userId) {
        URI uri;
        try {
            if (userId > 0) {
                uri = new URI(String.format(ROIS_URL, webServerUri, imageId, "&owner=" + userId));
                logger.debug("Getting shapes belonging to user with ID {} of image with ID {}", userId, imageId);
            } else {
                uri = new URI(String.format(ROIS_URL, webServerUri, imageId, ""));
                logger.debug("Getting all shapes of image with ID {}", imageId);
            }
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        Gson gson = new GsonBuilder().registerTypeAdapter(Shape.class, new Shape.GsonShapeDeserializer())
                .create();

        return requestSender.getPaginated(uri).thenApply(jsonElements -> jsonElements.stream()
                .map(jsonElement -> {
                    if (!jsonElement.isJsonObject()) {
                        throw new RuntimeException(String.format("The provided JSON element %s is not a JSON object", jsonElement));
                    }
                    JsonObject jsonObject = jsonElement.getAsJsonObject();

                    if (!jsonObject.has("@id") || !jsonObject.get("@id").isJsonPrimitive() ||
                            !jsonObject.getAsJsonPrimitive("@id").isNumber()
                    ) {
                        throw new RuntimeException(String.format("The number '@id' was not found in %s", jsonObject));
                    }
                    int roiID = jsonObject.get("@id").getAsInt();

                    if (!jsonObject.has("shapes") || !jsonObject.get("shapes").isJsonArray()) {
                        throw new RuntimeException(String.format("The array 'shapes' was not found in %s", jsonObject));
                    }
                    List<JsonElement> shapes = jsonElement.getAsJsonObject().getAsJsonArray("shapes").asList();

                    return shapes.stream()
                            .map(shape -> gson.fromJson(shape, Shape.class))
                            .filter(Objects::nonNull)
                            .peek(shape -> shape.setOldId(roiID))
                            .toList();
                })
                .flatMap(List::stream)
                .toList()
        );
    }

    /**
     * Attempt to re-login with the stored session UUID. This can be used as a last resort if the ping fails.
     * Note that this won't work if no authentication was previously performed.
     *
     * @return a CompletableFuture that completes exceptionally if the login fails
     */
    public CompletableFuture<Void> reLogin() {
        if (loginResponse == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No login previously done. Cannot re-attempt login"));
        }

        return authenticate(
                requestSender,
                links.login(),
                token,
                String.join(
                        "&",
                        String.format("server=%d", omeroServer.id()),
                        String.format("username=%s", loginResponse.sessionUuid()),
                        String.format("password=%s", loginResponse.sessionUuid())
                ).getBytes(StandardCharsets.UTF_8)
        ).thenAccept(loginResponse -> {});
    }

    private static String getLinksUrl(RequestSender requestSender, URI webServerUri) throws URISyntaxException, ExecutionException, InterruptedException {
        URI apiUri = new URI(String.format(API_URL, webServerUri));
        SupportedVersions supportedVersions = requestSender.getAndConvert(apiUri, SupportedVersions.class).get();

        if (supportedVersions.supportedVersions() == null) {
            throw new IllegalArgumentException(String.format("Unexpected response from %s. Cannot get JSON API version", apiUri));
        }
        List<SupportedVersion> supportedVersionsList = supportedVersions.supportedVersions();

        Optional<String> expectedVersion = supportedVersionsList.stream()
                .filter(supportedVersion -> EXPECTED_API_VERSION.equals(supportedVersion.version()))
                .map(SupportedVersion::versionURL)
                .filter(Objects::nonNull)
                .findAny();
        if (expectedVersion.isPresent()) {
            return expectedVersion.get();
        }

        Optional<SupportedVersion> unexpectedVersion = supportedVersionsList.stream()
                .filter(supportedVersion -> supportedVersion.version() != null && supportedVersion.versionURL() != null)
                .findAny();
        if (unexpectedVersion.isPresent()) {
            logger.warn(
                    "The expected version {} was not found, so version {} was chosen instead. This might cause issues later",
                    EXPECTED_API_VERSION,
                    unexpectedVersion.get().version()
            );
            return unexpectedVersion.get().versionURL();
        }

        throw new IllegalArgumentException(String.format("No supported version found in response of %s", apiUri));
    }

    private static Links getLinks(RequestSender requestSender, String linksUrl) throws URISyntaxException, ExecutionException, InterruptedException {
        URI linksUri = new URI(linksUrl);
        Links links = requestSender.getAndConvert(linksUri, Links.class).get();

        if (links.allLinksDefined()) {
            return links;
        } else {
            throw new IllegalArgumentException(String.format("Unexpected response from %s: not all expected links are present", linksUri));
        }
    }

    private static CompletableFuture<String> getToken(RequestSender requestSender, Links links) throws URISyntaxException {
        URI uri = new URI(links.token());

        return requestSender.getAndConvert(uri, Token.class).thenApply(token -> {
            if (token.token() == null) {
                throw new IllegalArgumentException(String.format("Unexpected response from %s: no token found", uri));
            } else {
                return token.token();
            }
        });
    }

    private static CompletableFuture<OmeroServer> getOmeroServer(RequestSender requestSender, Links links) throws URISyntaxException {
        URI uri = new URI(links.servers());

        return requestSender.getAndConvert(uri, OmeroServers.class).thenApply(omeroServers -> {
            List<OmeroServer> filteredList = omeroServers.servers() == null ?
                    List.of() :
                    omeroServers.servers().stream().filter(OmeroServer::allFieldsDefined).toList();
            logger.debug("OMERO servers filtered from {} to {}", omeroServers.servers(), filteredList);

            if (filteredList.isEmpty()) {
                throw new IllegalArgumentException(String.format("Unexpected response from %s: no servers found", uri));
            } else {
                OmeroServer omeroServer = filteredList.getFirst();

                if (filteredList.size() > 1) {
                    logger.warn("Several OMERO servers found: {}. {} will be used", omeroServers.servers(), omeroServer);
                }

                return omeroServer;
            }
        });
    }

    private static CompletableFuture<LoginResponse> login(
            RequestSender requestSender,
            Credentials credentials,
            String url,
            int serverId,
            String token
    ) {
        char[] encodedPassword = ApiUtilities.urlEncode(credentials.password());
        byte[] body = ApiUtilities.concatAndConvertToBytes(
                String.join("&", "server=" + serverId, "username=" + credentials.username(), "password=").toCharArray(),
                encodedPassword
        );

        return authenticate(requestSender, url, token, body);
    }

    private <T extends ServerEntity> LoadingCache<Long, T> createCache(Function<Long, String> idToUrl, Function<JsonElement, T> jsonToEntity) {
        return CacheBuilder.newBuilder()
                .maximumSize(SERVER_ENTITIES_CACHE_SIZE)
                .build(new CacheLoader<>() {
                    @Override
                    public T load(Long entityId) throws ExecutionException, InterruptedException {
                        logger.debug("Fetching entity with ID {} (not already in cache)", entityId);

                        return getEntity(
                                idToUrl.apply(entityId),
                                jsonToEntity
                        ).get();
                    }
                });
    }

    private <T extends ServerEntity> CompletableFuture<List<T>> getChildren(
            Cache<ServerEntityCacheKey, List<Long>> cache,
            LoadingCache<Long, T> cache2,
            ServerEntityCacheKey key,
            String url,
            Function<JsonElement, T> serverEntityCreator
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return cache.get(
                        key,
                        () -> {
                            logger.debug(
                                    "Fetching children of parent with ID {}, belonging to owner with ID {} and group with ID {} (not already in cache)",
                                    key.parentId(),
                                    key.ownerId(),
                                    key.groupId()
                            );

                            URI uri = URI.create(url);

                            synchronized (this) {
                                numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() + 1);
                            }

                            try {
                                List<T> entities = requestSender.getPaginated(uri).thenApply(jsonElements -> jsonElements.stream()
                                        .map(serverEntityCreator)
                                        .toList()
                                ).get();

                                for (T entity: entities) {
                                    cache2.put(entity.getId(), entity);
                                }

                                return entities.stream().map(ServerEntity::getId).toList();
                            } finally {
                                synchronized (this) {
                                    numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() - 1);
                                }
                            }
                        }
                ).stream().map(cache2::getUnchecked).toList();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static CompletableFuture<LoginResponse> authenticate(
            RequestSender requestSender,
            String url,
            String token,
            byte[] body
    ) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.post(
                        uri,
                        body,
                        uri.toString(),
                        token
                ).whenComplete((response, error) -> {
                    Arrays.fill(body, (byte) 0);    // clear possible password
                })
                .thenApply(response -> GsonTools.getInstance().fromJson(response, JsonObject.class))
                .thenApply(LoginResponse::parseServerAuthenticationResponse);
    }

    private <T extends ServerEntity> CompletableFuture<T> getEntity(String url, Function<JsonElement, T> serverEntityCreator) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        synchronized (this) {
            numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() + 1);
        }

        return requestSender.getAndConvert(uri, JsonObject.class).thenApply(response -> {
            if (!response.has("data")) {
                throw new RuntimeException(String.format("'data' member not present in %s", response));
            }

            return serverEntityCreator.apply(response.get("data"));
        }).whenComplete((entity, error) -> {
            synchronized (this) {
                numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() - 1);
            }
        });
    }
}
