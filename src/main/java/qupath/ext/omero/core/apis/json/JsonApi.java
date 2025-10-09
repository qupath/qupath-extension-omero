package qupath.ext.omero.core.apis.json;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableIntegerValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.apis.json.permissions.Experimenter;
import qupath.ext.omero.core.apis.json.permissions.ExperimenterGroup;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Image;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Well;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroDataset;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroPlate;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroPlateAcquisition;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroProject;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroScreen;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroWell;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroImage;
import qupath.ext.omero.core.apis.json.serverinformation.Links;
import qupath.ext.omero.core.apis.json.serverinformation.OmeroServer;
import qupath.ext.omero.core.apis.json.serverinformation.OmeroServers;
import qupath.ext.omero.core.apis.json.serverinformation.SupportedVersion;
import qupath.ext.omero.core.apis.json.serverinformation.SupportedVersions;
import qupath.ext.omero.core.apis.json.serverinformation.Token;
import qupath.ext.omero.core.apis.commonentities.shapes.Shape;
import qupath.ext.omero.core.apis.commonentities.shapes.ShapeCreator;
import qupath.lib.io.GsonTools;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
public class JsonApi {

    private static final Logger logger = LoggerFactory.getLogger(JsonApi.class);
    private static final int SERVER_ENTITIES_IDS_CACHE_SIZE = 10000;
    private static final int SERVER_ENTITIES_CACHE_SIZE = 1000;
    private static final String EXPECTED_API_VERSION = "0";
    private static final String API_URL = "%s/api/";
    private static final String GROUPS_OF_USER_URL = "%s%d/experimentergroups/";
    private static final String DATASETS_IN_PROJECT_URL = "%s%d/datasets/";
    private static final String IMAGES_IN_DATASET_URL = "%s%d/images/";
    private static final String PLATES_IN_SCREEN_URL = "%s%d/plates/";
    private static final String PLATE_ACQUISITION_URL = "%s/api/v0/m/plateacquisitions/%d";
    private static final String PLATE_ACQUISITIONS_IN_PLATE_URL = "%s/api/v0/m/plates/%d/plateacquisitions/";
    private static final String WELLS_URL = "%s/api/v0/m/wells/%d";
    private static final String WELLS_IN_PLATE_URL = "%s/api/v0/m/plates/%d/wells/";
    private static final String WELLS_IN_PLATE_ACQUISITION_URL = "%s/api/v0/m/plateacquisitions/%d/wellsampleindex/%d/wells/";
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
    private record ServerEntityCacheKey(long parentId, long experimenterId, long groupId) {}

    //TODO: handle https://omero.readthedocs.io/en/stable/developers/json-api.html#normalizing-experimenters-and-groups
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
     * @throws NullPointerException if the server doesn't return all necessary information
     */
    public JsonApi(URI webServerUri, RequestSender requestSender, Credentials credentials) throws URISyntaxException, ExecutionException, InterruptedException {
        this.webServerUri = webServerUri;
        this.requestSender = requestSender;

        this.links = requestSender.getAndConvert(new URI(getLinksUrl(requestSender, webServerUri)), Links.class).get();
        logger.debug("Got links {} from {}", links, webServerUri);

        // Send requests in parallel
        CompletableFuture<String> tokenRequest = requestSender.getAndConvert(new URI(links.token()), Token.class).thenApply(Token::data);
        CompletableFuture<OmeroServer> omeroServersRequest = getOmeroServer(requestSender, this.links);

        this.omeroServer = omeroServersRequest.get();
        logger.debug("Got OMERO server {} from {}", omeroServer, webServerUri);

        this.token = tokenRequest.get();
        logger.debug("Got token {} from {}", token, webServerUri);

        if (credentials.userType().equals(Credentials.UserType.REGULAR_USER)) {
            this.loginResponse = login(this.requestSender, credentials, links.login(), omeroServer.id(), token).get();

            logger.debug(
                    "Created JSON API with authenticated {} user of ID {} and default group of ID {}",
                    loginResponse.isAdmin() ? "admin" : "non admin",
                    loginResponse.userId(),
                    loginResponse.groupId()
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
     * {@link ApisHandler#getWebServerUri()}.
     *
     * @return the server host
     */
    public String getServerAddress() {
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
     * @return the ID of the default group of the connected user, or an empty Optional if no authentication was performed
     */
    public Optional<Long> getDefaultGroupId() {
        return Optional.ofNullable(loginResponse).map(LoginResponse::groupId);
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
     *         unless the provided user is the connected user and this user is an owner (or leader) of the group.
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
            uri = new URI(retrieveAllGroups ? links.groups() : String.format(GROUPS_OF_USER_URL, links.experimenters(), userId));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.getPaginated(uri).thenApplyAsync(jsonElements -> jsonElements.stream()
                .map(jsonElement -> {
                    try {
                        return gson.fromJson(jsonElement, OmeroExperimenterGroup.class);
                    } catch (JsonSyntaxException | NullPointerException e) {
                        logger.error("Cannot create experimenter group from {}", jsonElement, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(omeroGroup -> retrieveAllGroups || omeroGroup.name() == null || !GROUPS_TO_EXCLUDE.contains(omeroGroup.name()))
                .map(omeroGroup -> {
                    try {
                        return new ExperimenterGroup(
                                omeroGroup,
                                requestSender.getPaginated(new URI(omeroGroup.experimentersUrl())).get().stream()
                                        .map(jsonElement -> {
                                            try {
                                                return new Experimenter(gson.fromJson(jsonElement, OmeroExperimenter.class));
                                            } catch (JsonSyntaxException | NullPointerException e) {
                                                logger.error("Cannot create experimenter from {}", jsonElement, e);
                                                return null;
                                            }
                                        })
                                        .filter(Objects::nonNull)
                                        .filter(experimenter -> {
                                            if (retrieveAllGroups) {
                                                return true;
                                            } else if (loginResponse != null && userId == loginResponse.userId()) {
                                                return switch (omeroGroup.getPermissionLevel()) {
                                                    case PRIVATE -> experimenter.getId() == userId || loginResponse.ownedGroupIds().contains(omeroGroup.id());
                                                    case READ_ONLY, READ_ANNOTATE, READ_WRITE -> true;
                                                };
                                            } else {
                                                return switch (omeroGroup.getPermissionLevel()) {
                                                    case PRIVATE -> experimenter.getId() == userId;
                                                    case READ_ONLY, READ_ANNOTATE, READ_WRITE -> true;
                                                };
                                            }
                                        })
                                        .toList()
                        );
                    } catch (ExecutionException | InterruptedException | URISyntaxException | IllegalArgumentException | NullPointerException e) {
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
     * Attempt to retrieve projects belonging to the provided experimenter and group and visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param experimenterId the ID of the experimenter that should own the projects. Can be negative to not restrict by experimenter
     * @param groupId the ID of the group that should own the projects. Can be negative to not restrict by group
     * @return a CompletableFuture (that may complete exceptionally) with all projects belonging to the provided experimenter and group
     * and visible by the current user
     */
    public CompletableFuture<List<Project>> getProjects(long experimenterId, long groupId) {
        logger.debug("Getting projects belonging to experimenter with ID {} and group with ID {}", experimenterId, groupId);

        return getChildren(
                projectIdsCache,
                projectsCache,
                new ServerEntityCacheKey(-1, experimenterId, groupId),
                createUrlFromParameters(links.projects(), true, false, experimenterId, groupId),
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
     * Attempt to retrieve all datasets belonging to the provided project, experimenter and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param projectId the ID of the project whose datasets should be retrieved
     * @param experimenterId the ID of the experimenter that should own the datasets. Can be negative to not restrict by experimenter
     * @param groupId the ID of the group that should own the datasets. Can be negative to not restrict by group
     * @return a CompletableFuture (that may complete exceptionally) with all datasets belonging to the provided
     * project, experimenter and group
     */
    public CompletableFuture<List<Dataset>> getDatasets(long projectId, long experimenterId, long groupId) {
        logger.debug(
                "Getting datasets belonging to project with ID {}, experimenter with ID {} and group with ID {}",
                projectId,
                experimenterId,
                groupId
        );

        return getChildren(
                datasetIdsCache,
                datasetsCache,
                new ServerEntityCacheKey(projectId, experimenterId, groupId),
                createUrlFromParameters(
                        String.format(DATASETS_IN_PROJECT_URL, links.projects(), projectId),
                        true,
                        false,
                        experimenterId,
                        groupId
                ),
                jsonElement -> new Dataset(gson.fromJson(jsonElement, OmeroDataset.class), webServerUri)
        );
    }

    /**
     * Attempt to retrieve all orphaned datasets belonging to the provided experimenter and group and visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param experimenterId the ID of the experimenter that should own the datasets. Can be negative to not restrict by experimenter
     * @param groupId the ID of the group that should own the datasets. Can be negative to not restrict by group
     * @return a CompletableFuture (that may complete exceptionally) with all orphaned datasets belonging to the provided
     * experimenter and group
     */
    public CompletableFuture<List<Dataset>> getOrphanedDatasets(long experimenterId, long groupId) {
        logger.debug("Getting orphaned datasets belonging to experimenter with ID {} and group with ID {}", experimenterId, groupId);

        return getChildren(
                datasetIdsCache,
                datasetsCache,
                new ServerEntityCacheKey(-1, experimenterId, groupId),
                createUrlFromParameters(links.datasets(), true, true, experimenterId, groupId),
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
     * Attempt to retrieve all images belonging to the provided dataset, experimenter and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param datasetId the ID of the dataset whose images should be retrieved
     * @param experimenterId the ID of the experimenter that should own the datasets. Can be negative to not restrict by experimenter
     * @param groupId the ID of the group that should own the datasets. Can be negative to not restrict by group
     * @return a CompletableFuture (that may complete exceptionally) with all images belonging to the provided
     * dataset, experimenter and group
     */
    public CompletableFuture<List<Image>> getImages(long datasetId, long experimenterId, long groupId) {
        logger.debug("Getting images belonging to dataset with ID {}, experimenter with ID {} and group with ID {}", datasetId, experimenterId, groupId);

        return getChildren(
                imageIdsCache,
                imagesCache,
                new ServerEntityCacheKey(datasetId, experimenterId, groupId),
                createUrlFromParameters(
                        String.format(IMAGES_IN_DATASET_URL, links.datasets(), datasetId),
                        true,
                        false,
                        experimenterId,
                        groupId
                ),
                jsonElement -> new Image(gson.fromJson(jsonElement, OmeroImage.class), webServerUri)
        );
    }

    /**
     * Attempt to retrieve all orphaned images belonging to the provided experimenter and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param experimenterId the ID of the experimenter that should own the datasets. Can be negative to not restrict by experimenter
     * @param groupId the ID of the group that should own the datasets. Can be negative to not restrict by group
     * @return a CompletableFuture (that may complete exceptionally) with all orphaned images belonging to the provided
     * experimenter and group
     */
    public CompletableFuture<List<Image>> getOrphanedImages(long experimenterId, long groupId) {
        logger.debug("Getting orphaned images belonging to experimenter with ID {} and group with ID {}", experimenterId, groupId);

        return getChildren(
                imageIdsCache,
                imagesCache,
                new ServerEntityCacheKey(-1, experimenterId, groupId),
                createUrlFromParameters(links.images(), false, true, experimenterId, groupId),
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
     * Attempt to retrieve screens belonging to the provided experimenter and group and visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param experimenterId the ID of the experimenter that should own the datasets. Can be negative to not restrict by experimenter
     * @param groupId the ID of the group that should own the datasets. Can be negative to not restrict by group
     * @return a CompletableFuture (that may complete exceptionally) with all screens belonging to the provided experimenter and group
     * and visible by the current user
     */
    public CompletableFuture<List<Screen>> getScreens(long experimenterId, long groupId) {
        logger.debug("Getting screens belonging to experimenter with ID {} and group with ID {}", experimenterId, groupId);

        return getChildren(
                screenIdsCache,
                screensCache,
                new ServerEntityCacheKey(-1, experimenterId, groupId),
                createUrlFromParameters(links.screens(), true, false, experimenterId, groupId),
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
     * Attempt to retrieve all plates belonging to the provided screen, experimenter and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param screenId the ID of the screen whose plates should be retrieved
     * @param experimenterId the ID of the experimenter that should own the datasets. Can be negative to not restrict by experimenter
     * @param groupId the ID of the group that should own the datasets. Can be negative to not restrict by group
     * @return a CompletableFuture (that may complete exceptionally) with all plates belonging to the provided
     * screen, experimenter and group
     */
    public CompletableFuture<List<Plate>> getPlates(long screenId, long experimenterId, long groupId) {
        logger.debug("Getting plates belonging to screen with ID {}, experimenter with ID {} and group with ID {}", screenId, experimenterId, groupId);

        return getChildren(
                plateIdsCache,
                platesCache,
                new ServerEntityCacheKey(screenId, experimenterId, groupId),
                createUrlFromParameters(
                        String.format(PLATES_IN_SCREEN_URL, links.screens(), screenId),
                        true,
                        false,
                        experimenterId, groupId
                ),
                jsonElement -> new Plate(gson.fromJson(jsonElement, OmeroPlate.class), webServerUri)
        );
    }

    /**
     * Attempt to retrieve all orphaned plates belonging to the provided experimenter and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param experimenterId the ID of the experimenter that should own the datasets. Can be negative to not restrict by experimenter
     * @param groupId the ID of the group that should own the datasets. Can be negative to not restrict by group
     * @return a CompletableFuture (that may complete exceptionally) with all orphaned plates belonging to the provided
     * experimenter and group
     */
    public CompletableFuture<List<Plate>> getOrphanedPlates(long experimenterId, long groupId) {
        logger.debug("Getting orphaned plates belonging to experimenter with ID {} and group with ID {}", experimenterId, groupId);

        return getChildren(
                plateIdsCache,
                platesCache,
                new ServerEntityCacheKey(-1, experimenterId, groupId),
                createUrlFromParameters(links.plates(), true, true, experimenterId, groupId),
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
     * Attempt to retrieve all plate acquisitions belonging to the provided plate, experimenter and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateId the ID of the plate whose plate acquisitions should be retrieved
     * @param experimenterId the ID of the experimenter that should own the datasets. Can be negative to not restrict by experimenter
     * @param groupId the ID of the group that should own the datasets. Can be negative to not restrict by group
     * @param numberOfWells the number of wells each returned plate acquisition should have
     * @return a CompletableFuture (that may complete exceptionally) with all plate acquisitions belonging to the provided
     * plate, experimenter and group
     */
    public CompletableFuture<List<PlateAcquisition>> getPlateAcquisitions(long plateId, long experimenterId, long groupId, int numberOfWells) {
        logger.debug(
                "Getting plate acquisitions belonging to plate with ID {}, experimenter with ID {} and group with ID {}",
                plateId,
                experimenterId,
                groupId
        );

        return getChildren(
                plateAcquisitionIdsCache,
                plateAcquisitionsCache,
                new ServerEntityCacheKey(plateId, experimenterId, groupId),
                createUrlFromParameters(
                        String.format(PLATE_ACQUISITIONS_IN_PLATE_URL, webServerUri, plateId),
                        true,
                        false,
                        experimenterId,
                        groupId
                ),
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
     * Attempt to retrieve all wells belonging to the provided plate, experimenter and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateId the ID of the plate whose wells should be retrieved
     * @param experimenterId the ID of the experimenter that should own the datasets. Can be negative to not restrict by experimenter
     * @param groupId the ID of the group that should own the datasets. Can be negative to not restrict by group
     * @return a CompletableFuture (that may complete exceptionally) with all wells belonging to the provided
     * plate, experimenter and group
     */
    public CompletableFuture<List<Well>> getWellsFromPlate(long plateId, long experimenterId, long groupId) {
        logger.debug("Getting wells belonging to plate with ID {}, experimenter with ID {} and group with ID {}", plateId, experimenterId, groupId);

        return getChildren(
                wellIdsCache,
                wellsCache,
                new ServerEntityCacheKey(plateId, experimenterId, groupId),
                createUrlFromParameters(
                        String.format(WELLS_IN_PLATE_URL, webServerUri, plateId),
                        true,
                        false,
                        experimenterId,
                        groupId
                ),
                jsonElement -> new Well(
                        gson.fromJson(jsonElement, OmeroWell.class),
                        -1,
                        webServerUri
                )
        );
    }

    /**
     * Attempt to retrieve all wells belonging to the provided plate acquisition, experimenter and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateAcquisitionId the ID of the plate acquisition whose wells should be retrieved
     * @param experimenterId the ID of the experimenter that should own the datasets. Can be negative to not restrict by experimenter
     * @param groupId the ID of the group that should own the datasets. Can be negative to not restrict by group
     * @param wellSampleIndex the index of the well sample owning the wells to retrieve
     * @return a CompletableFuture (that may complete exceptionally) with all wells belonging to the provided
     * plate acquisition, experimenter and group
     */
    public CompletableFuture<List<Well>> getWellsFromPlateAcquisition(long plateAcquisitionId, long experimenterId, long groupId, int wellSampleIndex) {
        logger.debug(
                "Getting wells belonging to plate acquisition with ID {}, experimenter with ID {}, group with ID {} and well sample index {}",
                plateAcquisitionId,
                experimenterId,
                groupId,
                wellSampleIndex
        );

        return getChildren(
                wellIdsCache,
                wellsCache,
                new ServerEntityCacheKey(-plateAcquisitionId, experimenterId, groupId),        // use negative ID to use same cache as wells from plate
                createUrlFromParameters(
                        String.format(WELLS_IN_PLATE_ACQUISITION_URL, webServerUri, plateAcquisitionId, wellSampleIndex),
                        true,
                        false,
                        experimenterId,
                        groupId
                ),
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
                    int roiId = jsonObject.get("@id").getAsInt();

                    if (!jsonObject.has("shapes") || !jsonObject.get("shapes").isJsonArray()) {
                        throw new RuntimeException(String.format("The array 'shapes' was not found in %s", jsonObject));
                    }
                    List<JsonElement> shapes = jsonElement.getAsJsonObject().getAsJsonArray("shapes").asList();

                    return shapes.stream()
                            .map(shape -> ShapeCreator.createShape(shape, roiId))
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

        Optional<String> expectedVersion = supportedVersions.supportedVersions().stream()
                .filter(supportedVersion -> EXPECTED_API_VERSION.equals(supportedVersion.version()))
                .map(SupportedVersion::versionUrl)
                .findAny();
        if (expectedVersion.isPresent()) {
            return expectedVersion.get();
        }

        Optional<SupportedVersion> unexpectedVersion = supportedVersions.supportedVersions().stream()
                .filter(supportedVersion -> supportedVersion.version() != null && supportedVersion.versionUrl() != null)
                .findAny();
        if (unexpectedVersion.isPresent()) {
            logger.warn(
                    "The expected version {} was not found, so version {} was chosen instead. This might cause issues later",
                    EXPECTED_API_VERSION,
                    unexpectedVersion.get().version()
            );
            return unexpectedVersion.get().versionUrl();
        }

        throw new IllegalArgumentException(String.format("No supported version found in response of %s", apiUri));
    }

    private static CompletableFuture<OmeroServer> getOmeroServer(RequestSender requestSender, Links links) throws URISyntaxException {
        URI uri = new URI(links.servers());

        return requestSender.getAndConvert(uri, OmeroServers.class).thenApply(omeroServers -> {
            if (omeroServers.servers().isEmpty()) {
                throw new IllegalArgumentException(String.format("Unexpected response from %s: no servers found", uri));
            } else {
                OmeroServer omeroServer = omeroServers.servers().getFirst();

                if (omeroServers.servers().size() > 1) {
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
        char[] encodedPassword = Utils.urlEncode(credentials.password());
        byte[] body = Utils.concatAndConvertToBytes(
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

    private static String createUrlFromParameters(String baseUrl, boolean childCount, boolean orphaned, long experimenterId, long groupId) {
        List<String> parameters = new ArrayList<>();
        if (childCount) {
            parameters.add("childCount=true");
        }
        if (orphaned) {
            parameters.add("orphaned=true");
        }
        if (experimenterId > -1) {
            parameters.add(String.format("owner=%d", experimenterId));
        }
        if (groupId > -1) {
            parameters.add(String.format("group=%d", groupId));
        }

        return String.format("%s?%s", baseUrl, String.join("&", parameters));
    }

    private <T extends ServerEntity> CompletableFuture<List<T>> getChildren(
            Cache<ServerEntityCacheKey, List<Long>> idCache,
            LoadingCache<Long, T> entityCache,
            ServerEntityCacheKey key,
            String url,
            Function<JsonElement, T> serverEntityCreator
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return idCache.get(
                        key,
                        () -> {
                            logger.debug(
                                    "Fetching children of parent with ID {}, belonging to experimenter with ID {} and group with ID {} (not already in cache)",
                                    key.parentId(),
                                    key.experimenterId(),
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
                                    entityCache.put(entity.getId(), entity);
                                }

                                return entities.stream().map(ServerEntity::getId).toList();
                            } finally {
                                synchronized (this) {
                                    numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() - 1);
                                }
                            }
                        }
                ).stream().map(entityCache::getUnchecked).toList();
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
