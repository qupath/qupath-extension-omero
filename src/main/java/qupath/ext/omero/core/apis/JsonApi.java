package qupath.ext.omero.core.apis;

import com.drew.lang.annotations.Nullable;
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
import qupath.ext.omero.core.apis.authenticators.Authenticator;
import qupath.ext.omero.core.entities.login.LoginResponse;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.*;
import qupath.ext.omero.core.entities.shapes.Shape;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.serverinformation.OmeroAPI;
import qupath.ext.omero.core.entities.serverinformation.OmeroServerList;
import qupath.ext.omero.core.RequestSender;
import qupath.lib.io.GsonTools;

import java.net.PasswordAuthentication;
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
 * <p>The OMERO <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html">JSON API</a>.</p>
 * <p>
 *     This API is used to get basic information on the server, authenticate, get details on OMERO entities
 *     (e.g. images, datasets), and get ROIs of an image.
 * </p>
 */
class JsonApi {

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
    private static final String ROIS_URL = "%s/api/v0/m/rois/?image=%s";
    private static final List<String> GROUPS_TO_EXCLUDE = List.of("system", "user");
    private final IntegerProperty numberOfEntitiesLoading = new SimpleIntegerProperty(0);
    private final IntegerProperty numberOfOrphanedImagesLoaded = new SimpleIntegerProperty(0);
    private final URI webURI;
    private final Map<String, String> urls;
    private final int serverID;
    private final String serverURI;
    private final int port;
    private final String token;

    private JsonApi(URI webURI, Map<String, String> urls, int serverID, String serverURI, int port, String token) {
        this.webURI = webURI;
        this.urls = urls;
        this.serverID = serverID;
        this.serverURI = serverURI;
        this.port = port;
        this.token = token;
    }

    @Override
    public String toString() {
        return String.format("JSON API of %s", webURI);
    }

    /**
     * <p>Attempt to create a JSON API client.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param uri the web server URI (e.g. <a href="https://idr.openmicroscopy.org">https://idr.openmicroscopy.org</a>)
     * @return a CompletableFuture (that may complete exceptionally) with the JSON API client
     */
    public static CompletableFuture<JsonApi> create(URI uri) {
        URI apiURI;
        try {
            apiURI = new URI(String.format(API_URL, uri));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return RequestSender.getAndConvert(apiURI, OmeroAPI.class)
                .thenCompose(omeroAPI -> {
                    if (omeroAPI.getLatestVersionURL().isPresent()) {
                        try {
                            return RequestSender.getAndConvert(new URI(omeroAPI.getLatestVersionURL().get()), new TypeToken<Map<String, String>>() {});
                        } catch (URISyntaxException e) {
                            return CompletableFuture.failedFuture(e);
                        }
                    } else {
                        throw new RuntimeException("The latest version of the API supported by the server was not found");
                    }
                })
                .thenApplyAsync(urls -> {
                    OmeroServerList serverInformation;
                    String token;
                    try {
                        serverInformation = getServerInformation(urls).get();
                        token = getToken(urls).get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }

                    if (serverInformation.getServerHost().isPresent() &&
                            serverInformation.getServerId().isPresent() &&
                            serverInformation.getServerPort().isPresent()) {
                        return new JsonApi(
                                uri,
                                urls,
                                serverInformation.getServerId().getAsInt(),
                                serverInformation.getServerHost().get(),
                                serverInformation.getServerPort().getAsInt(),
                                token
                        );
                    } else {
                        throw new RuntimeException(String.format(
                                "The retrieved server information %s does not have all the required information (host, id, and port)",
                                serverInformation
                        ));
                    }
                });
    }

    /**
     * @return the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a> used by this session
     */
    public String getToken() {
        return token;
    }

    /**
     * <p>
     *     Get the server URI of this server. This is the <b>OMERO server</b>
     *     URI and may be different from the <b>OMERO web</b> URI.
     * </p>
     * <p>
     *     The returned address is the address used by OMERO web to communicate
     *     with an OMERO server. If these two entities are running on the same server,
     *     the returned value of this function may be {@code localhost} or any local IP.
     *     Therefore, if you can't communicate with the returned value of this function,
     *     you should be able to communicate with the address of OMERO web (returned by
     *     {@link ApisHandler#getWebServerURI()}.
     * </p>
     *
     * @return the server host
     */
    public String getServerURI() {
        return serverURI;
    }

    /**
     * @return the server port of this server. This is the OMERO server
     * port and may be different from the OMERO web port
     */
    public int getServerPort() {
        return port;
    }

    /**
     * @return the number of OMERO entities (e.g. datasets, images) currently being loaded by the API.
     * This property may be updated from any thread
     */
    public ReadOnlyIntegerProperty getNumberOfEntitiesLoading() {
        return numberOfEntitiesLoading;
    }

    /**
     * <p>Attempt to authenticate to the current server.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if a request failed for example).
     * </p>
     *
     * <p>The arguments must have one of the following format:</p>
     * <ul>
     *     <li>{@code --username [username] --password [password]}</li>
     *     <li>{@code -u [username] -p [password]}</li>
     * </ul>
     * <p>
     *     If the arguments are not enough to authenticate, the user will
     *     automatically be asked for credentials.
     * </p>
     *
     * @return a CompletableFuture (that may complete exceptionally) with the authentication status
     */
    public CompletableFuture<LoginResponse> login(@Nullable String username, @Nullable String password) {
        URI uri;
        try {
            uri = new URI(urls.get(LOGIN_URL_KEY));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        PasswordAuthentication authentication = username == null || password == null ?
                Authenticator.getPasswordAuthentication(webURI.toString()) :
                new PasswordAuthentication(username, password.toCharArray());

        if (authentication == null) {
            return CompletableFuture.completedFuture(LoginResponse.createNonAuthenticatedLoginResponse(LoginResponse.Status.CANCELED));
        } else {
            char[] encodedPassword = ApiUtilities.urlEncode(authentication.getPassword());

            byte[] body = ApiUtilities.concatAndConvertToBytes(
                    String.join("&", "server=" + serverID, "username=" + authentication.getUserName(), "password=").toCharArray(),
                    encodedPassword
            );

            return RequestSender.post(
                    uri,
                    body,
                    uri.toString(),
                    token
            ).whenComplete((response, error) -> {
                Arrays.fill(body, (byte) 0);    // clear password
            })
                    .thenApply(response -> GsonTools.getInstance().fromJson(response, JsonObject.class))
                    .thenApply(LoginResponse::createAuthenticatedLoginResponse);
        }
    }

    /**
     * <p>
     *     Attempt to retrieve all groups of a user. This doesn't include the system and user groups.
     * </p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if a request failed for example).
     * </p>
     *
     * @param userId the ID of the user that belong to the returned groups
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all groups of the provided user
     */
    public CompletableFuture<List<Group>> getGroups(long userId) {
        URI uri;
        try {
            uri = new URI(String.format(GROUPS_OF_USER_URL, urls.get(OWNERS_URL_KEY), userId));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return RequestSender.getPaginated(uri).thenApplyAsync(jsonElements -> {
            List<Group> groups = jsonElements.stream()
                    .map(jsonElement -> new Gson().fromJson(jsonElement, Group.class))
                    .filter(group -> !GROUPS_TO_EXCLUDE.contains(group.getName()))
                    .toList();

            for (Group group: groups) {
                URI experimenterLink = URI.create(group.getExperimentersLink());

                group.setOwners(
                        RequestSender.getPaginated(experimenterLink).join().stream()
                                .map(jsonElement -> new Gson().fromJson(jsonElement, Owner.class))
                                .filter(owner -> !group.isPrivate() || owner.id() == userId)
                                .toList()
                );
            }

            return groups;
        });
    }

    /**
     * <p>Attempt to retrieve all projects visible by the current user.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all projects of this server
     */
    public CompletableFuture<List<Project>> getProjects() {
        return getChildren(String.format(PROJECTS_URL, urls.get(PROJECTS_URL_KEY))).thenApply(
                children -> children.stream().map(child -> (Project) child).toList()
        );
    }

    /**
     * <p>Attempt to retrieve all orphaned datasets visible by the current user.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all orphaned datasets of this server
     */
    public CompletableFuture<List<Dataset>> getOrphanedDatasets() {
        return getChildren(String.format(ORPHANED_DATASETS_URL, urls.get(DATASETS_URL_KEY))).thenApply(
                children -> children.stream().map(child -> (Dataset) child).toList()
        );
    }

    /**
     * <p>Attempt to retrieve all datasets visible by the current user.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param projectID  the project ID whose datasets should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all datasets of the project
     */
    public CompletableFuture<List<Dataset>> getDatasets(long projectID) {
        return getChildren(String.format(DATASETS_URL, urls.get(PROJECTS_URL_KEY), projectID)).thenApply(
                children -> children.stream().map(child -> (Dataset) child).toList()
        );
    }

    /**
     * <p>Attempt to retrieve all images of a dataset visible by the current user.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param datasetID  the dataset ID whose images should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all images of the dataset
     */
    public CompletableFuture<List<Image>> getImages(long datasetID) {
        return getChildren(String.format(IMAGES_URL, urls.get(DATASETS_URL_KEY), datasetID)).thenApply(
                children -> children.stream().map(child -> (Image) child).toList()
        );
    }

    /**
     * <p>Attempt to create an Image entity from an image ID.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param imageID the ID of the image
     * @return a CompletableFuture (that may complete exceptionally) with the image
     */
    public CompletableFuture<Image> getImage(long imageID) {
        URI uri;
        try {
            uri = new URI(urls.get(IMAGES_URL_KEY) + imageID);
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        synchronized (this) {
            numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() + 1);
        }

        return RequestSender.getAndConvert(uri, JsonObject.class)
                .thenApply(jsonImage -> {
                    if (!jsonImage.has("data")) {
                        throw new RuntimeException(String.format("'data' member not present in %s", jsonImage));
                    }

                    ServerEntity serverEntity = ServerEntity.createFromJsonElement(jsonImage.get("data"), webURI);
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
     * <p>
     *     Populate all orphaned images visible by the current user to the list specified in parameter.
     *     This function populates and doesn't return a list because the number of images can
     *     be large, so this operation can take tens of seconds and should be run in a background thread.
     * </p>
     * <p>The list can be updated from any thread.</p>
     *
     * @param children the list which should be populated by the orphaned images. It should
     *                 be possible to add elements to this list
     * @param orphanedImagesIds the Ids of all orphaned images of the server
     * @throws IllegalArgumentException when an orphaned image ID is invalid
     * @throws CancellationException when the computation is cancelled
     * @throws CompletionException when a request fails
     * @throws JsonSyntaxException when a conversion from JSON to image fails
     * @throws ClassCastException when a conversion from JSON to image fails
     */
    public void populateOrphanedImagesIntoList(List<Image> children, List<Long> orphanedImagesIds) {
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
                    .map(jsonObject -> ServerEntity.createFromJsonElement(jsonObject, webURI))
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
     * <p>Attempt to retrieve all screens visible by the current user.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @return a CompletableFuture (that may complete exceptionally) with a list containing all screen of this server
     */
    public CompletableFuture<List<Screen>> getScreens() {
        return getChildren(String.format(SCREENS_URL, urls.get(SCREENS_URL_KEY))).thenApply(
                children -> children.stream().map(child -> (Screen) child).toList()
        );
    }

    /**
     * <p>Attempt to retrieve all orphaned (e.g. not in any screen) plates visible by the current user.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all orphaned plates of this server
     */
    public CompletableFuture<List<Plate>> getOrphanedPlates() {
        return getChildren(String.format(ORPHANED_PLATES_URL, urls.get(PLATES_URL_KEY))).thenApply(
                children -> children.stream().map(child -> (Plate) child).toList()
        );
    }

    /**
     * <p>Attempt to retrieve all plates visible by the current user.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param screenID the screen ID whose plates should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all plates of the screen
     */
    public CompletableFuture<List<Plate>> getPlates(long screenID) {
        return getChildren(String.format(PLATES_URL, urls.get(SCREENS_URL_KEY), screenID)).thenApply(
                children -> children.stream().map(child -> (Plate) child).toList()
        );
    }

    /**
     * <p>Attempt to retrieve all plate acquisitions of a plate visible by the current user.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param plateID the plate ID whose plate acquisitions should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all plate acquisitions of the plate
     */
    public CompletableFuture<List<PlateAcquisition>> getPlateAcquisitions(long plateID) {
        return getChildren(String.format(PLATE_ACQUISITIONS_URL, webURI, plateID)).thenApply(
                children -> children.stream().map(child -> (PlateAcquisition) child).toList()
        );
    }

    /**
     * <p>Attempt to retrieve all wells of a plate visible by the current user.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param plateID the plate acquisition ID whose wells should be retrieved
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all wells of the plate
     */
    public CompletableFuture<List<Well>> getWellsFromPlate(long plateID) {
        return getChildren(String.format(PLATE_WELLS_URL, webURI, plateID)).thenApply(
                children -> children.stream().map(child -> (Well) child).toList()
        );
    }

    /**
     * <p>Attempt to retrieve all wells of a plate acquisition visible by the current user.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param plateAcquisitionID the plate acquisition ID whose wells should be retrieved
     * @param wellSampleIndex the index of the well sample
     * @return a CompletableFuture (that may complete exceptionally) with the list containing all wells of the plate acquisition
     */
    public CompletableFuture<List<Well>> getWellsFromPlateAcquisition(long plateAcquisitionID, int wellSampleIndex) {
        return getChildren(String.format(WELLS_URL, webURI, plateAcquisitionID, wellSampleIndex)).thenApply(
                children -> children.stream().map(child -> (Well) child).toList()
        );
    }

    /**
     * <p>Indicates if the server can be browsed without being authenticated.</p>
     * <p>This works only if the client isn't already authenticated to the server.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @return a void CompletableFuture (that completes exceptionally if authentication cannot be skipped)
     */
    public CompletableFuture<Void> canSkipAuthentication() {
        try {
            return RequestSender.isLinkReachableWithGet(new URI(urls.get(PROJECTS_URL_KEY)));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * <p>
     *     Attempt to retrieve ROIs of an image.
     * </p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request or the conversion failed for example).
     * </p>
     *
     * @param id the OMERO image ID
     * @return a CompletableFuture (that may complete exceptionally) with the list of ROIs
     */
    public CompletableFuture<List<Shape>> getROIs(long id) {
        URI uri;
        try {
            uri = new URI(String.format(ROIS_URL, webURI, id));
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        Gson gson = new GsonBuilder().registerTypeAdapter(Shape.class, new Shape.GsonShapeDeserializer())
                .setStrictness(Strictness.LENIENT)
                .create();

        return RequestSender.getPaginated(uri).thenApply(jsonElements -> jsonElements.stream()
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

    private static CompletableFuture<OmeroServerList> getServerInformation(Map<String, String> urls) {
        String token = SERVERS_URL_KEY;

        if (urls.containsKey(token)) {
            try {
                return RequestSender.getAndConvert(new URI(urls.get(token)), OmeroServerList.class);
            } catch (URISyntaxException e) {
                return CompletableFuture.failedFuture(e);
            }
        } else {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    String.format("The %s token was not found in %s", token, urls)
            ));
        }
    }

    private static CompletableFuture<String> getToken(Map<String, String> urls) {
        String token = TOKEN_URL_KEY;

        if (urls.containsKey(token)) {
            URI uri;
            try {
                uri = new URI(urls.get(token));
            } catch (URISyntaxException e) {
                return CompletableFuture.failedFuture(e);
            }

            return RequestSender.getAndConvert(uri, new TypeToken<Map<String, String>>() {}).thenApply(response -> {
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

        return RequestSender.getPaginated(uri)
                .thenApply(jsonElements ->
                        jsonElements.stream()
                                .map(jsonElement -> ServerEntity.createFromJsonElement(jsonElement, webURI))
                                .toList()
                )
                .whenComplete((entities, error) -> {
                    synchronized (this) {
                        numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() - 1);
                    }
                });
    }

    private CompletableFuture<JsonObject> requestImageInfo(URI uri) {
        return RequestSender.getAndConvert(uri, JsonObject.class).thenApply(response -> {
            if (response.has("data") && response.get("data").isJsonObject()) {
                return response.get("data").getAsJsonObject();
            } else {
                throw new RuntimeException(String.format("'data' JSON object not found in %s", response));
            }
        });
    }
}
