package qupath.ext.omero.core.apis;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import com.google.gson.reflect.TypeToken;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final String WELLS_URL = "%s/api/v0/m/plateacquisitions/%d/wellsampleindex/%d/wells/";
    private static final String ROIS_URL = "%s/api/v0/m/rois/?image=%d%s";
    private static final List<String> GROUPS_TO_EXCLUDE = List.of("system", "user");
    private final IntegerProperty numberOfEntitiesLoading = new SimpleIntegerProperty(0);
    private final IntegerProperty numberOfOrphanedImagesLoaded = new SimpleIntegerProperty(0);
    private final URI webServerUri;
    private final RequestSender requestSender;
    private final Map<String, String> urls;
    private final String serverUri;
    private final int port;
    private final String token;
    private final Group defaultGroup;
    private final long userId;
    private final String sessionUuid;

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
     * @throws IllegalArgumentException if the server doesn't return all necessary information on it
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
            LoginResponse loginResponse = login(this.requestSender, credentials, this.urls, serverInformation.getServerId().getAsInt(), token);
            this.defaultGroup = loginResponse.group();
            this.userId = loginResponse.userId();
            this.sessionUuid = loginResponse.sessionUuid();

            logger.debug("Created JSON API with authenticated user of ID {} and default group {}", userId, defaultGroup);
        } else {
            this.defaultGroup = null;
            this.userId = -1;
            sessionUuid = null;

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
     * @return the user ID of the connected user, or -1 if no authentication was performed
     */
    public long getUserId() {
        return userId;
    }

    /**
     * @return the default group of the connected user, or null if no authentication was performed
     */
    public Group getDefaultGroup() {
        return defaultGroup;
    }

    /**
     * @return the session UUID of the current connection, or null if no authentication was performed
     */
    public String getSessionUuid() {
        return sessionUuid;
    }

    /**
     * @return the number of OMERO entities (e.g. datasets, images) currently being loaded by the API.
     * This property may be updated from any thread
     */
    public ReadOnlyIntegerProperty getNumberOfEntitiesLoading() {
        return numberOfEntitiesLoading;
    }

    /**
     * Attempt to retrieve all groups of a user. This doesn't include the system and user groups.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if a request failed for example).
     *
     * @param userId the ID of the user that belong to the returned groups
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all groups of the provided user
     */
    public CompletableFuture<List<Group>> getGroups(long userId) {
        logger.debug("Getting groups of user with ID {}", userId);

        URI uri;
        try {
            uri = new URI(String.format(GROUPS_OF_USER_URL, urls.get(OWNERS_URL_KEY), userId));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return requestSender.getPaginated(uri).thenApplyAsync(jsonElements -> {
            List<Group> groups = jsonElements.stream()
                    .map(jsonElement -> new Gson().fromJson(jsonElement, Group.class))
                    .filter(group -> !GROUPS_TO_EXCLUDE.contains(group.getName()))
                    .toList();

            for (Group group: groups) {
                URI experimenterLink = URI.create(group.getExperimentersLink());

                group.setOwners(
                        requestSender.getPaginated(experimenterLink).join().stream()
                                .map(jsonElement -> new Gson().fromJson(jsonElement, Owner.class))
                                .filter(owner -> !group.isPrivate() || owner.id() == userId)
                                .toList()
                );
            }

            return groups;
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

        return getChildren(String.format(PROJECTS_URL, urls.get(PROJECTS_URL_KEY))).thenApply(
                children -> children.stream().map(child -> (Project) child).toList()
        );
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

        return getChildren(String.format(ORPHANED_DATASETS_URL, urls.get(DATASETS_URL_KEY))).thenApply(
                children -> children.stream().map(child -> (Dataset) child).toList()
        );
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

        return getChildren(String.format(DATASETS_URL, urls.get(PROJECTS_URL_KEY), projectId)).thenApply(
                children -> children.stream().map(child -> (Dataset) child).toList()
        );
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

        return getChildren(String.format(IMAGES_URL, urls.get(DATASETS_URL_KEY), datasetId)).thenApply(
                children -> children.stream().map(child -> (Image) child).toList()
        );
    }

    /**
     * Attempt to create an Image entity from an image ID.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param imageId the ID of the image
     * @return a CompletableFuture (that may complete exceptionally) with the image
     */
    public CompletableFuture<Image> getImage(long imageId) {
        logger.debug("Getting information on image with ID {}", imageId);

        URI uri;
        try {
            uri = new URI(urls.get(IMAGES_URL_KEY) + imageId);
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        synchronized (this) {
            numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() + 1);
        }

        return requestSender.getAndConvert(uri, JsonObject.class)
                .thenApply(jsonImage -> {
                    if (!jsonImage.has("data")) {
                        throw new RuntimeException(String.format("'data' member not present in %s", jsonImage));
                    }

                    ServerEntity serverEntity = ServerEntity.createFromJsonElement(jsonImage.get("data"), webServerUri);
                    if (serverEntity instanceof Image image) {
                        return image;
                    } else {
                        throw new RuntimeException(String.format("The returned server entity %s is not an image", serverEntity));
                    }
                })
                .whenComplete((jsonImage, error) -> {
                    synchronized (this) {
                        numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() - 1);
                    }
                });
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

            synchronized (this) {
                numberOfOrphanedImagesLoaded.set(numberOfOrphanedImagesLoaded.get() + batch.size());
            }
        }
    }

    /**
     * @return the number of orphaned images which have been loaded.
     * This property may be updated from any thread
     */
    public ReadOnlyIntegerProperty getNumberOfOrphanedImagesLoaded() {
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

        return getChildren(String.format(SCREENS_URL, urls.get(SCREENS_URL_KEY))).thenApply(
                children -> children.stream().map(child -> (Screen) child).toList()
        );
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

        return getChildren(String.format(ORPHANED_PLATES_URL, urls.get(PLATES_URL_KEY))).thenApply(
                children -> children.stream().map(child -> (Plate) child).toList()
        );
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

        return getChildren(String.format(PLATES_URL, urls.get(SCREENS_URL_KEY), screenId)).thenApply(
                children -> children.stream().map(child -> (Plate) child).toList()
        );
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

        return getChildren(String.format(PLATE_ACQUISITIONS_URL, webServerUri, plateId)).thenApply(
                children -> children.stream().map(child -> (PlateAcquisition) child).toList()
        );
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

        return getChildren(String.format(PLATE_WELLS_URL, webServerUri, plateId)).thenApply(
                children -> children.stream().map(child -> (Well) child).toList()
        );
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

        return getChildren(String.format(WELLS_URL, webServerUri, plateAcquisitionId, wellSampleIndex)).thenApply(
                children -> children.stream().map(child -> (Well) child).toList()
        );
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
        logger.debug("Getting shapes of image with ID {}", imageId);

        URI uri;
        try {
            if (userId > 0) {
                uri = new URI(String.format(ROIS_URL, webServerUri, imageId, "&owner=" + userId));
            } else {
                uri = new URI(String.format(ROIS_URL, webServerUri, imageId, ""));
            }
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        Gson gson = new GsonBuilder().registerTypeAdapter(Shape.class, new Shape.GsonShapeDeserializer())
                .setStrictness(Strictness.LENIENT)
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

    private static Map<String, String> getUrls(RequestSender requestSender, URI apiURI) throws ExecutionException, InterruptedException, URISyntaxException {
        OmeroApi omeroAPI = requestSender.getAndConvert(apiURI, OmeroApi.class).get();

        if (omeroAPI.getLatestVersionURL().isPresent()) {
            return requestSender.getAndConvert(new URI(omeroAPI.getLatestVersionURL().get()), new TypeToken<Map<String, String>>() {}).get();
        } else {
            throw new IllegalArgumentException("The latest version of the API supported by the server was not found");
        }
    }

    private static CompletableFuture<String> getToken(RequestSender requestSender, Map<String, String> urls) {
        String token = TOKEN_URL_KEY;

        if (urls.containsKey(token)) {
            URI uri;
            try {
                uri = new URI(urls.get(token));
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
        } else {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    String.format("The %s token was not found in %s", token, urls)
            ));
        }
    }

    private static CompletableFuture<OmeroServerList> getServerInformation(RequestSender requestSender, Map<String, String> urls) {
        String token = SERVERS_URL_KEY;

        if (urls.containsKey(token)) {
            try {
                return requestSender.getAndConvert(new URI(urls.get(token)), OmeroServerList.class);
            } catch (URISyntaxException e) {
                return CompletableFuture.failedFuture(e);
            }
        } else {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    String.format("The %s token was not found in %s", token, urls)
            ));
        }
    }

    private static LoginResponse login(
            RequestSender requestSender,
            Credentials credentials,
            Map<String, String> urls,
            int serverID,
            String token
    ) throws URISyntaxException, ExecutionException, InterruptedException {
        URI uri = new URI(urls.get(LOGIN_URL_KEY));

        char[] encodedPassword = ApiUtilities.urlEncode(credentials.password());

        byte[] body = ApiUtilities.concatAndConvertToBytes(
                String.join("&", "server=" + serverID, "username=" + credentials.username(), "password=").toCharArray(),
                encodedPassword
        );

        return requestSender.post(
                        uri,
                        body,
                        uri.toString(),
                        token
                ).whenComplete((response, error) -> {
                    Arrays.fill(body, (byte) 0);    // clear password
                })
                .thenApply(response -> GsonTools.getInstance().fromJson(response, JsonObject.class))
                .thenApply(LoginResponse::parseServerAuthenticationResponse).get();
    }

    private CompletableFuture<List<ServerEntity>> getChildren(String url) {
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
                .thenApply(jsonElements ->
                        jsonElements.stream()
                                .map(jsonElement -> ServerEntity.createFromJsonElement(jsonElement, webServerUri))
                                .toList()
                )
                .whenComplete((entities, error) -> {
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
}
