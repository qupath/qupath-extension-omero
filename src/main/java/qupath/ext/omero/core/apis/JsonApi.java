package qupath.ext.omero.core.apis;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableIntegerValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.entities.LoginResponse;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Well;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.serverinformation.OmeroApi;
import qupath.ext.omero.core.entities.serverinformation.OmeroServerList;
import qupath.ext.omero.core.entities.shapes.Shape;
import qupath.lib.io.GsonTools;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * The OMERO <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html">JSON API</a>.
 * <p>
 * This API is used to get basic information on the server, authenticate, get details on OMERO entities
 * (e.g. images, datasets), and get ROIs of an image.
 */
class JsonApi {

    private static final Logger logger = LoggerFactory.getLogger(JsonApi.class);
    private static final String OWNERS_URL_KEY = "url:experimenters";
    private static final String GROUPS_URL_KEY = "url:experimentergroups";
    private static final String PROJECTS_URL_KEY = "url:projects";
    private static final String DATASETS_URL_KEY = "url:datasets";
    private static final String IMAGES_URL_KEY = "url:images";
    private static final String SCREENS_URL_KEY = "url:screens";
    private static final String PLATES_URL_KEY = "url:plates";
    private static final String TOKEN_URL_KEY = "url:token";
    private static final String SERVERS_URL_KEY = "url:servers";
    private static final String LOGIN_URL_KEY = "url:login";
    private static final String API_URL = "%s/api/";
    private static final String GROUPS_OF_USER_URL = "%s%d/experimentergroups/";
    private static final String PROJECTS_URL = "%s?childCount=true";
    private static final String DATASETS_URL = "%s%d/datasets/?childCount=true";
    private static final String IMAGES_URL = "%s%d/images/?childCount=true";
    private static final String ORPHANED_DATASETS_URL = "%s?childCount=true&orphaned=true";
    private static final String SCREENS_URL = "%s?childCount=true";
    private static final String PLATES_URL = "%s%d/plates/";
    private static final String ORPHANED_PLATES_URL = "%s?orphaned=true";
    private static final String PLATE_ACQUISITIONS_URL = "%s/api/v0/m/plates/%d/plateacquisitions/";
    private static final String PLATE_WELLS_URL = "%s/api/v0/m/plates/%d/wells/";
    private static final String WELLS_IN_PLATE_ACQUISITION_URL = "%s/api/v0/m/plateacquisitions/%d/wellsampleindex/%d/wells/";
    private static final String PLATE_ACQUISITION_URL = "%s/api/v0/m/plateacquisitions/%d";
    private static final String WELLS_URL = "%s/api/v0/m/wells/%d";
    private static final String ROIS_URL = "%s/api/v0/m/rois/?image=%d%s";
    private static final List<String> GROUPS_TO_EXCLUDE = List.of("system", "user");
    private static final Gson gson = new Gson();
    private final IntegerProperty numberOfEntitiesLoading = new SimpleIntegerProperty(0);
    private final IntegerProperty numberOfOrphanedImagesLoaded = new SimpleIntegerProperty(0);
    private final URI webServerUri;
    private final RequestSender requestSender;
    private final Map<String, String> urls;
    private final String serverUri;
    private final int serverId;
    private final int port;
    private final String token;
    private final LoginResponse loginResponse;

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
        this.urls = getUrls(requestSender, new URI(String.format(API_URL, webServerUri)));

        CompletableFuture<String> tokenRequest = getToken(requestSender, this.urls);
        CompletableFuture<OmeroServerList> serverInformationRequest = getServerInformation(requestSender, this.urls);

        OmeroServerList serverInformation = serverInformationRequest.get();
        if (serverInformation.getServerHost().isPresent() &&
                serverInformation.getServerId().isPresent() &&
                serverInformation.getServerPort().isPresent()) {
            this.serverUri = serverInformation.getServerHost().get();
            this.serverId = serverInformation.getServerId().getAsInt();
            this.port = serverInformation.getServerPort().getAsInt();

            logger.debug("OMERO.server information set: {}:{}", this.serverUri, this.port);
        } else {
            throw new IllegalArgumentException(String.format(
                    "The retrieved server information %s does not have all the required information (host, id, and port)",
                    serverInformation
            ));
        }

        this.token = tokenRequest.get();
        if (credentials.userType().equals(Credentials.UserType.REGULAR_USER)) {
            this.loginResponse = login(this.requestSender, credentials, this.urls, this.serverId, token).get();
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
        return serverUri;
    }

    /**
     * @return the server port of this server. This is the OMERO server
     * port and may be different from the OMERO web port
     */
    public int getServerPort() {
        return port;
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
     * Attempt to retrieve all groups of a user or of the server.
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
    public CompletableFuture<List<Group>> getGroups(long userId) {
        boolean retrieveAllGroups = userId < 0;

        if (retrieveAllGroups) {
            logger.debug("Getting all groups of the server");
        } else {
            logger.debug("Getting groups of user with ID {}", userId);
        }

        URI uri;
        try {
            uri = new URI(retrieveAllGroups ? urls.get(GROUPS_URL_KEY) : String.format(GROUPS_OF_USER_URL, urls.get(OWNERS_URL_KEY), userId));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.getPaginated(uri).thenApplyAsync(jsonElements -> {
            List<Group> groups = jsonElements.stream()
                    .map(jsonElement -> gson.fromJson(jsonElement, Group.class))
                    .toList();
            List<Group> filteredGroups = groups.stream()
                    .filter(group -> retrieveAllGroups || !GROUPS_TO_EXCLUDE.contains(group.getName()))
                    .toList();
            logger.debug("Groups {} filtered to {} retrieved", groups, filteredGroups);

            for (Group group: filteredGroups) {
                URI experimenterLink = URI.create(group.getExperimentersLink());
                List<Owner> owners = requestSender.getPaginated(experimenterLink).join().stream()
                        .map(jsonElement -> gson.fromJson(jsonElement, Owner.class))
                        .toList();

                group.setOwners(owners.stream()
                        .filter(owner -> {
                            if (retrieveAllGroups) {
                                return true;
                            } else if (loginResponse != null && userId == loginResponse.userId()) {
                                return switch (group.getPermissionLevel()) {
                                    case PRIVATE -> owner.id() == userId || loginResponse.ownedGroupIds().contains(group.getId());
                                    case READ_ONLY, READ_ANNOTATE, READ_WRITE -> true;
                                    case UNKNOWN -> owner.id() == userId;
                                };
                            } else {
                                return switch (group.getPermissionLevel()) {
                                    case PRIVATE, UNKNOWN -> owner.id() == userId;
                                    case READ_ONLY, READ_ANNOTATE, READ_WRITE -> true;
                                };
                            }
                        })
                        .toList()
                );
                logger.debug("Owners {} have been filtered to {} and assigned to {}", owners, group.getOwners(), group);
            }

            return filteredGroups;
        });
    }

    /**
     * Attempt to retrieve all projects visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all projects of this server
     */
    public CompletableFuture<List<Project>> getProjects() {
        logger.debug("Getting all projects visible by the current user");

        return getChildren(String.format(PROJECTS_URL, urls.get(PROJECTS_URL_KEY)), Project.class);
    }

    /**
     * Attempt to retrieve a project entity from a project ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param projectId the ID of the project
     * @return a CompletableFuture (that may complete exceptionally) with the project
     */
    public CompletableFuture<Project> getProject(long projectId) {
        logger.debug("Getting project with ID {}", projectId);

        return getEntity(urls.get(PROJECTS_URL_KEY) + projectId, Project.class);
    }

    /**
     * Attempt to retrieve all orphaned datasets visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all orphaned datasets of this server
     */
    public CompletableFuture<List<Dataset>> getOrphanedDatasets() {
        logger.debug("Getting all orphaned datasets visible by the current user");

        return getChildren(String.format(ORPHANED_DATASETS_URL, urls.get(DATASETS_URL_KEY)), Dataset.class);
    }

    /**
     * Attempt to retrieve all datasets of the provided project.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param projectId the project ID whose datasets should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all datasets of the project
     */
    public CompletableFuture<List<Dataset>> getDatasets(long projectId) {
        logger.debug("Getting all datasets of project with ID {}", projectId);

        return getChildren(String.format(DATASETS_URL, urls.get(PROJECTS_URL_KEY), projectId), Dataset.class);
    }

    /**
     * Attempt to retrieve a dataset entity from a dataset ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param datasetId the ID of the dataset
     * @return a CompletableFuture (that may complete exceptionally) with the dataset
     */
    public CompletableFuture<Dataset> getDataset(long datasetId) {
        logger.debug("Getting dataset with ID {}", datasetId);

        return getEntity(urls.get(DATASETS_URL_KEY) + datasetId, Dataset.class);
    }

    /**
     * Attempt to retrieve all images of a dataset visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param datasetId the dataset ID whose images should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all images of the dataset
     */
    public CompletableFuture<List<Image>> getImages(long datasetId) {
        logger.debug("Getting all images of dataset with ID {}", datasetId);

        return getChildren(String.format(IMAGES_URL, urls.get(DATASETS_URL_KEY), datasetId), Image.class);
    }

    /**
     * Attempt to retrieve an image entity from an image ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param imageId the ID of the image
     * @return a CompletableFuture (that may complete exceptionally) with the image
     */
    public CompletableFuture<Image> getImage(long imageId) {
        logger.debug("Getting image with ID {}", imageId);

        return getEntity(urls.get(IMAGES_URL_KEY) + imageId, Image.class);
    }

    /**
     * Populate the orphaned images with the provided IDs to the list specified in parameter.
     * This function populates and doesn't return a list because the number of images can
     * be large, so this operation can take tens of seconds and should be run in a background thread.
     * <p>
     * The list can be updated from any thread.
     *
     * @param children the list which should be populated by the orphaned images. It should
     *                 be possible to add elements to this list
     * @param orphanedImagesIds the IDs of all orphaned images that should be added to the list
     * @throws IllegalArgumentException when an orphaned image ID is invalid
     * @throws CancellationException when the computation is cancelled
     * @throws CompletionException when a request fails
     * @throws JsonSyntaxException when a conversion from JSON to image fails
     * @throws ClassCastException when a conversion from JSON to image fails
     */
    public void populateOrphanedImagesIntoList(List<Image> children, List<Long> orphanedImagesIds) {
        logger.debug("Populating orphaned images with IDs {}", orphanedImagesIds);

        synchronized (this) {
            numberOfOrphanedImagesLoaded.set(0);
        }

        List<URI> orphanedImagesURIs = orphanedImagesIds.stream()
                .map(id -> URI.create(urls.get(IMAGES_URL_KEY) + id))
                .toList();

        // The number of parallel requests is limited to 16
        // to avoid too many concurrent streams
        List<List<URI>> batches = Lists.partition(orphanedImagesURIs, 16);
        for (List<URI> batch: batches) {
            children.addAll(batch.stream()
                    .map(this::requestImageInfo)
                    .map(CompletableFuture::join)
                    .map(jsonObject -> ServerEntity.createFromJsonElement(jsonObject, webServerUri))
                    .map(serverEntity -> (Image) serverEntity)
                    .toList()
            );
            logger.debug("{} orphaned images retrieved", batch.size());

            synchronized (this) {
                numberOfOrphanedImagesLoaded.set(numberOfOrphanedImagesLoaded.get() + batch.size());
            }
        }
    }

    /**
     * @return the number of orphaned images which have been loaded.
     * This property may be updated from any thread
     */
    public ObservableIntegerValue getNumberOfOrphanedImagesLoaded() {
        return numberOfOrphanedImagesLoaded;
    }

    /**
     * Attempt to retrieve all screens visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with a list containing all screen of this server
     */
    public CompletableFuture<List<Screen>> getScreens() {
        logger.debug("Getting all screens visible by the current user");

        return getChildren(String.format(SCREENS_URL, urls.get(SCREENS_URL_KEY)), Screen.class);
    }

    /**
     * Attempt to retrieve a screen entity from a screen ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param screenId the ID of the screen
     * @return a CompletableFuture (that may complete exceptionally) with the screen
     */
    public CompletableFuture<Screen> getScreen(long screenId) {
        logger.debug("Getting screen with ID {}", screenId);

        return getEntity(urls.get(SCREENS_URL_KEY) + screenId, Screen.class);
    }

    /**
     * Attempt to retrieve all orphaned (e.g. not in any screen) plates visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all orphaned plates of this server
     */
    public CompletableFuture<List<Plate>> getOrphanedPlates() {
        logger.debug("Getting all orphaned plates visible by the current user");

        return getChildren(String.format(ORPHANED_PLATES_URL, urls.get(PLATES_URL_KEY)), Plate.class);
    }

    /**
     * Attempt to retrieve all plates visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param screenId the screen ID whose plates should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all plates of the screen
     */
    public CompletableFuture<List<Plate>> getPlates(long screenId) {
        logger.debug("Getting all plates of screen with ID {}", screenId);

        return getChildren(String.format(PLATES_URL, urls.get(SCREENS_URL_KEY), screenId), Plate.class);
    }

    /**
     * Attempt to retrieve a plate entity from a plate ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateId the ID of the plate
     * @return a CompletableFuture (that may complete exceptionally) with the plate
     */
    public CompletableFuture<Plate> getPlate(long plateId) {
        logger.debug("Getting plate with ID {}", plateId);

        return getEntity(urls.get(PLATES_URL_KEY) + plateId, Plate.class);
    }

    /**
     * Attempt to retrieve all plate acquisitions of a plate visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateId the plate ID whose plate acquisitions should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all plate acquisitions of the plate
     */
    public CompletableFuture<List<PlateAcquisition>> getPlateAcquisitions(long plateId) {
        logger.debug("Getting all plate acquisitions of plate with ID {}", plateId);

        return getChildren(String.format(PLATE_ACQUISITIONS_URL, webServerUri, plateId), PlateAcquisition.class);
    }

    /**
     * Attempt to retrieve a plate acquisition entity from a plate acquisition ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateAcquisitionId the ID of the plate acquisition
     * @return a CompletableFuture (that may complete exceptionally) with the plate acquisition
     */
    public CompletableFuture<PlateAcquisition> getPlateAcquisition(long plateAcquisitionId) {
        logger.debug("Getting plate acquisition with ID {}", plateAcquisitionId);

        return getEntity(String.format(PLATE_ACQUISITION_URL, webServerUri, plateAcquisitionId), PlateAcquisition.class);
    }

    /**
     * Attempt to retrieve all wells of a plate visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateId the plate acquisition ID whose wells should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all wells of the plate
     */
    public CompletableFuture<List<Well>> getWellsFromPlate(long plateId) {
        logger.debug("Getting all wells of plate with ID {}", plateId);

        return getChildren(String.format(PLATE_WELLS_URL, webServerUri, plateId), Well.class);
    }

    /**
     * Attempt to retrieve all wells of a plate acquisition visible by the current user.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param plateAcquisitionId the plate acquisition ID whose wells should be retrieved
     * @param wellSampleIndex the index of the well sample
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all wells of the plate acquisition
     */
    public CompletableFuture<List<Well>> getWellsFromPlateAcquisition(long plateAcquisitionId, int wellSampleIndex) {
        logger.debug("Getting all wells of plate acquisition with ID {} and well sample of ID {}", plateAcquisitionId, wellSampleIndex);

        return getChildren(String.format(WELLS_IN_PLATE_ACQUISITION_URL, webServerUri, plateAcquisitionId, wellSampleIndex), Well.class);
    }

    /**
     * Attempt to retrieve a well entity from a well ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param wellId the ID of the well
     * @return a CompletableFuture (that may complete exceptionally) with the well
     */
    public CompletableFuture<Well> getWell(long wellId) {
        logger.debug("Getting well with ID {}", wellId);

        return getEntity(String.format(WELLS_URL, webServerUri, wellId), Well.class);
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
                urls,
                token,
                String.join(
                        "&",
                        String.format("server=%d", serverId),
                        String.format("username=%s", loginResponse.sessionUuid()),
                        String.format("password=%s", loginResponse.sessionUuid())
                ).getBytes(StandardCharsets.UTF_8)
        ).thenAccept(loginResponse -> {});
    }

    private static Map<String, String> getUrls(RequestSender requestSender, URI apiUri) throws ExecutionException, InterruptedException, URISyntaxException {
        OmeroApi omeroAPI = requestSender.getAndConvert(apiUri, OmeroApi.class).get();

        if (omeroAPI.getLatestVersionURL().isPresent()) {
            return requestSender.getAndConvert(new URI(omeroAPI.getLatestVersionURL().get()), new TypeToken<Map<String, String>>() {}).get();
        } else {
            throw new IllegalArgumentException("The latest version of the API supported by the server was not found");
        }
    }

    private static CompletableFuture<String> getToken(RequestSender requestSender, Map<String, String> urls) {
        if (!urls.containsKey(TOKEN_URL_KEY)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    String.format("The %s token was not found in %s", TOKEN_URL_KEY, urls)
            ));
        }

        URI uri;
        try {
            uri = new URI(urls.get(TOKEN_URL_KEY));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.getAndConvert(uri, new TypeToken<Map<String, String>>() {}).thenApply(response -> {
            if (response.containsKey("data")) {
                return response.get("data");
            } else {
                throw new IllegalArgumentException(String.format("'data' field not found in %s", response));
            }
        });
    }

    private static CompletableFuture<OmeroServerList> getServerInformation(RequestSender requestSender, Map<String, String> urls) {
        if (!urls.containsKey(SERVERS_URL_KEY)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    String.format("The %s token was not found in %s", SERVERS_URL_KEY, urls)
            ));
        }

        try {
            return requestSender.getAndConvert(new URI(urls.get(SERVERS_URL_KEY)), OmeroServerList.class);
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static CompletableFuture<LoginResponse> login(
            RequestSender requestSender,
            Credentials credentials,
            Map<String, String> urls,
            int serverId,
            String token
    ) {
        char[] encodedPassword = ApiUtilities.urlEncode(credentials.password());
        byte[] body = ApiUtilities.concatAndConvertToBytes(
                String.join("&", "server=" + serverId, "username=" + credentials.username(), "password=").toCharArray(),
                encodedPassword
        );

        return authenticate(requestSender, urls, token, body);
    }

    private <T extends ServerEntity> CompletableFuture<List<T>> getChildren(String url, Class<T> type) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        synchronized (this) {
            numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() + 1);
        }

        return requestSender.getPaginated(uri)
                .thenApply(jsonElements -> jsonElements.stream()
                        .map(jsonElement -> ServerEntity.createFromJsonElement(jsonElement, webServerUri))
                        .map(type::cast)
                        .toList()
                )
                .whenComplete((entities, error) -> {
                    synchronized (this) {
                        numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() - 1);
                    }
                });
    }

    private <T extends ServerEntity> CompletableFuture<T> getEntity(String url, Class<T> type) {
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

            ServerEntity serverEntity = ServerEntity.createFromJsonElement(response.get("data"), webServerUri);
            if (serverEntity.getClass().equals(type)) {
                return type.cast(serverEntity);
            } else {
                throw new RuntimeException(String.format("The returned server entity %s is not a %s", serverEntity, type));
            }
        }).whenComplete((entity, error) -> {
            synchronized (this) {
                numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() - 1);
            }
        });
    }

    private CompletableFuture<JsonObject> requestImageInfo(URI uri) {
        return requestSender.getAndConvert(uri, JsonObject.class).thenApply(response -> {
            if (response.has("data") && response.get("data").isJsonObject()) {
                return response.get("data").getAsJsonObject();
            } else {
                throw new RuntimeException(String.format("'data' JSON object not found in %s", response));
            }
        });
    }

    private static CompletableFuture<LoginResponse> authenticate(
            RequestSender requestSender,
            Map<String, String> urls,
            String token,
            byte[] body
    ) {
        if (!urls.containsKey(LOGIN_URL_KEY)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    String.format("The %s token was not found in %s", LOGIN_URL_KEY, urls)
            ));
        }

        URI uri;
        try {
            uri = new URI(urls.get(LOGIN_URL_KEY));
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
}
