package qupath.ext.omero;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ExecConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.commonentities.image.ChannelSettings;
import qupath.ext.omero.core.apis.commonentities.image.ImageSettings;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;
import qupath.ext.omero.core.apis.json.permissions.Experimenter;
import qupath.ext.omero.core.apis.json.permissions.ExperimenterGroup;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.webclient.EntityType;
import qupath.ext.omero.core.apis.webclient.SimpleServerEntity;
import qupath.ext.omero.core.apis.webclient.annotations.Annotation;
import qupath.ext.omero.core.apis.webclient.annotations.CommentAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.FileAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.OmeroSimpleExperimenter;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroAnnotationExperimenter;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroCommentAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroFile;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroFileAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroLink;
import qupath.ext.omero.core.apis.webclient.search.SearchResult;
import qupath.ext.omero.core.pixelapis.mspixelbuffer.MsPixelBufferApi;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.PixelType;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * An abstract class that gives access to an OMERO server hosted
 * on a local Docker container. Each subclass of this class will
 * have access to the same OMERO server.
 * <p>
 * The OMERO server is populated by several projects, datasets, images,
 * users, groups. The <a href="https://github.com/glencoesoftware/omero-ms-pixel-buffer">OMERO
 * Pixel Data Microservice</a> is also installed on the server.
 * <p>
 * All useful information of the OMERO server are returned by functions
 * of this class.
 * <p>
 * If Docker can't be found on the host machine, all tests are skipped.
 * <p>
 * If a Docker container containing a working OMERO server is already
 * running on the host machine, set the {@link #IS_LOCAL_OMERO_SERVER_RUNNING}
 * variable to {@code true}. This will prevent this class to create new containers,
 * gaining some time when running the tests.
 * A local OMERO server can be started by running the qupath-extension-omero/src/test/resources/server.sh
 * script.
 */
public abstract class OmeroServer {

    private static final Logger logger = LoggerFactory.getLogger(OmeroServer.class);
    private static final boolean IS_LOCAL_OMERO_SERVER_RUNNING = false;
    private static final int CLIENT_CREATION_ATTEMPTS = 3;
    private static final String OMERO_PASSWORD = "password";
    private static final int OMERO_SERVER_PORT = 4064;
    private static final int OMERO_WEB_PORT = 4080;
    private static final int MS_PIXEL_BUFFER_PORT = 8082;
    private static final boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
    private static final GenericContainer<?> postgres;
    private static final GenericContainer<?> redis;
    private static final GenericContainer<?> omeroServer;
    private static final GenericContainer<?> omeroWeb;
    protected enum UserType {
        UNAUTHENTICATED,
        AUTHENTICATED,
        ADMIN
    }

    static {
        if (!dockerAvailable || IS_LOCAL_OMERO_SERVER_RUNNING) {
            postgres = null;
            redis = null;
            omeroServer = null;
            omeroWeb = null;
        } else {
            // See https://hub.docker.com/r/openmicroscopy/omero-server
            postgres = new GenericContainer<>(DockerImageName.parse("postgres"))
                    .withNetwork(Network.SHARED)
                    .withNetworkAliases("postgres")
                    .withEnv("POSTGRES_PASSWORD", "postgres")
                    .withLogConsumer(frame ->
                            logger.debug("Postgres container: {}", frame.getUtf8String())
                    );

            omeroServer = new GenericContainer<>(DockerImageName.parse("openmicroscopy/omero-server"))
                    .withNetwork(Network.SHARED)
                    .withNetworkAliases("omero-server")
                    .withEnv("CONFIG_omero_db_host", "postgres")
                    .withEnv("CONFIG_omero_db_user", "postgres")
                    .withEnv("CONFIG_omero_db_pass", "postgres")
                    .withEnv("CONFIG_omero_db_name", "postgres")
                    .withEnv("ROOTPASS", OMERO_PASSWORD)
                    .withExposedPorts(OMERO_SERVER_PORT)
                    // Wait for the server to accept connections
                    .waitingFor(new AbstractWaitStrategy() {
                        @Override
                        protected void waitUntilReady() {
                            try (RequestSender requestSender = new RequestSender()) {
                                while (true) {
                                    logger.info("Attempting to connect to the OMERO server");

                                    try {
                                        requestSender.isLinkReachable(
                                                URI.create(getWebServerURI()),
                                                RequestSender.RequestType.GET,
                                                false,
                                                true
                                        ).get();
                                        logger.info("Connection to the OMERO server succeeded");
                                        return;
                                    } catch (Exception e) {
                                        logger.info("Connection to the OMERO server failed. Retrying in five seconds.", e);
                                    }

                                    try {
                                        TimeUnit.SECONDS.sleep(5);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    })
                    // Send resources (images, installation script) to the container
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("omero-server", 0777),
                            "/resources"
                    )
                    .dependsOn(postgres)
                    .withLogConsumer(frame ->
                            logger.debug("OMERO server container: {}", frame.getUtf8String())
                    );

            // See https://github.com/glencoesoftware/omero-ms-pixel-buffer:
            // OMERO.web needs to use Redis backed sessions
            redis = new GenericContainer<>(DockerImageName.parse("redis"))
                    .withNetwork(Network.SHARED)
                    .withNetworkAliases("redis")
                    .withLogConsumer(frame ->
                            logger.debug("Redis container: {}", frame.getUtf8String())
                    );

            // See https://hub.docker.com/r/openmicroscopy/omero-web-standalone
            omeroWeb = new GenericContainer<>(DockerImageName.parse("openmicroscopy/omero-web-standalone"))
                    .withNetwork(Network.SHARED)
                    .withEnv("OMEROHOST", "omero-server")
                    // Enable public user (see https://omero.readthedocs.io/en/stable/sysadmins/public.html#configuring-public-user)
                    .withEnv("CONFIG_omero_web_public_enabled", "True")
                    .withEnv("CONFIG_omero_web_public_user", "public")
                    .withEnv("CONFIG_omero_web_public_password", "password_public")
                    .withEnv("CONFIG_omero_web_public_url__filter", "(.*?)")
                    // Setup Django cached session (see https://omero.readthedocs.io/en/stable/sysadmins/config.html#omero-web-caches
                    // and https://omero.readthedocs.io/en/stable/sysadmins/config.html#omero-web-session-engine)
                    .withEnv("CONFIG_omero_web_caches", "{\"default\": {\"BACKEND\": \"django_redis.cache.RedisCache\",\"LOCATION\": \"redis://redis:6379/0\"}}")
                    .withEnv("CONFIG_omero_web_session__engine", "django.contrib.sessions.backends.cache")
                    .withExposedPorts(OMERO_WEB_PORT, MS_PIXEL_BUFFER_PORT)
                    .waitingFor(Wait.forListeningPorts(OMERO_WEB_PORT))
                    // Send resources (pixel buffer microservice files, installation script) to the container
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("omero-web", 0777),
                            "/resources"
                    )
                    .dependsOn(redis)
                    .withLogConsumer(frame ->
                            logger.debug("OMERO web container: {}", frame.getUtf8String())
                    );

            omeroWeb.start();
            omeroServer.start();

            try {
                // Wait for omero server to be ready
                boolean serverNotReady = true;
                while (serverNotReady) {
                    Container.ExecResult rootConnectionResult = omeroServer.execInContainer(
                            "/opt/omero/server/venv3/bin/omero", "login", "root@localhost:4064", "-w", "password"
                    );

                    serverNotReady = rootConnectionResult.getStderr().contains("Exception");
                    if (serverNotReady) {
                        logger.debug("Connection to OMERO failed: {}\nWaiting 1 second and retrying...", rootConnectionResult.getStderr());
                        TimeUnit.SECONDS.sleep(1);
                    }
                }

                // Set up the OMERO server (by creating users, importing images...)
                Container.ExecResult omeroServerSetupResult = omeroServer.execInContainer("/resources/setup.sh");
                logCommandResult(omeroServerSetupResult);

                // Copy the /OMERO directory from the OMERO server container to the OMERO web container.
                // This is needed for the pixel buffer microservice to work
                Path omeroFolderPath = Paths.get(System.getProperty("java.io.tmpdir"), "OMERO.tar.gz");
                omeroServer.copyFileFromContainer("/tmp/OMERO.tar.gz", omeroFolderPath.toString());
                omeroWeb.copyFileToContainer(MountableFile.forHostPath(omeroFolderPath, 0777), "/tmp/OMERO.tar.gz");

                // Set up the OMERO web container (by installing the pixel buffer microservice)
                Container.ExecResult omeroWebInstallPixelMsResult = omeroWeb.execInContainer(
                        ExecConfig.builder()
                                .user("root")
                                .command(new String[] {"/resources/installPixelBufferMs.sh"})
                                .build()
                );
                logCommandResult(omeroWebInstallPixelMsResult);

                // Set up the OMERO web container (by starting the pixel buffer microservice)
                Container.ExecResult omeroWebRunPixelMsResult = omeroWeb.execInContainer(
                        ExecConfig.builder()
                                .user("root")
                                .command(new String[] {"/resources/runPixelBufferMs.sh"})
                                .build()
                );
                logCommandResult(omeroWebRunPixelMsResult);
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @BeforeAll
    static void shouldRunTest() {
        Assumptions.assumeTrue(dockerAvailable, "Aborting test: no docker environment detected");
    }

    /**
     * @return the URL of this OMERO server
     */
    protected static String getWebServerURI() {
        return omeroWeb == null ?
                "http://localhost:" + OMERO_WEB_PORT :
                "http://" + omeroWeb.getHost() + ":" + omeroWeb.getMappedPort(OMERO_WEB_PORT);
    }

    /**
     * @return the credentials to use when connecting with the provided user type
     */
    protected static Credentials getCredentials(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new Credentials();
            case AUTHENTICATED, ADMIN -> new Credentials(
                    getUsername(userType),
                    getPassword(userType).toCharArray()
            );
        };
    }

    /**
     * @return the address of the OMERO.server instance of this server
     */
    protected static String getServerAddress() {
        return "omero-server";
    }

    /**
     * @return the port of the OMERO.server instance of this server
     */
    protected static int getServerPort() {
        return OMERO_SERVER_PORT;
    }

    /**
     * @return a created client corresponding to the provided user type
     */
    protected static Client createClient(UserType userType) {
        Credentials credentials = getCredentials(userType);
        Client client = null;
        int attempt = 0;

        do {
            try {
                client = Client.createOrGet(getWebServerURI(), credentials, null);
            } catch (Exception e) {
                logger.debug("Client creation attempt {} of {} failed", attempt, CLIENT_CREATION_ATTEMPTS - 1, e);
            }
        } while (client != null && ++attempt < CLIENT_CREATION_ATTEMPTS);

        if (client == null) {
            throw new IllegalStateException("Client creation failed");
        } else {
            client.getPixelAPI(MsPixelBufferApi.class).setPort(getMsPixelBufferApiPort(), true);
            return client;
        }
    }

    /**
     * @return the username of the provided user type
     */
    protected static String getUsername(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> "public";
            case AUTHENTICATED -> "user";
            case ADMIN -> "admin";
        };
    }

    /**
     * @return the password of the provided user type
     */
    protected static String getPassword(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> "password_user";
            case UNAUTHENTICATED -> "password_public";
            case ADMIN -> "password_admin";
        };
    }

    /**
     * @return the groups the provided user type belongs to
     */
    protected static List<ExperimenterGroup> getGroups(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> List.of(
                    getGroup1(),
                    getGroup2(),
                    getGroup3()
            );
            case UNAUTHENTICATED -> List.of(
                    getPublicGroup()
            );
            case ADMIN -> getGroups();
        };
    }

    /**
     * @return all groups of this server
     */
    protected static List<ExperimenterGroup> getGroups() {
        return List.of(
                getSystemGroup(),
                getUserGroup(),
                getGuestGroup(),
                getPublicGroup(),
                getGroup1(),
                getGroup2(),
                getGroup3()
        );
    }

    /**
     * @return the default group of the provided user type
     */
    protected static ExperimenterGroup getDefaultGroup(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> getGroup1();
            case UNAUTHENTICATED -> getPublicGroup();
            case ADMIN -> getSystemGroup();
        };
    }

    /**
     * @return the groups the provided user type owns
     */
    protected static List<ExperimenterGroup> getGroupsOwnedByUser(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> List.of();
            case AUTHENTICATED -> List.of(getGroup3());
        };
    }

    /**
     * @return all experimenters visible by the provided user type
     */
    protected static List<Experimenter> getExperimenters(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> List.of(
                    getUser1(),
                    getUser2(),
                    getUser3(),
                    getUser()
            );
            case UNAUTHENTICATED -> List.of(
                    getPublicUser()
            );
            case ADMIN -> List.of(
                    getRootUser(),
                    getGuestUser(),
                    getAdminUser(),
                    getPublicUser(),
                    getUser1(),
                    getUser2(),
                    getUser3(),
                    getUser()
            );
        };
    }

    /**
     * @return the experimenter corresponding to the user type
     */
    protected static Experimenter getConnectedExperimenter(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> getUser();
            case UNAUTHENTICATED -> getPublicUser();
            case ADMIN -> getAdminUser();
        };
    }

    /**
     * @return the ID and full name of the experimenter owning server entities of the provided user type
     */
    protected static SimpleEntity getEntityOwner(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleEntity(getPublicUser().getId(), getPublicUser().getFullName());
            case AUTHENTICATED -> new SimpleEntity(getUser1().getId(), getUser1().getFullName());
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the ID and name of the group owning server entities of the provided user type
     */
    protected static SimpleEntity getEntityGroup(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleEntity(getPublicGroup().getId(), getPublicGroup().getName().orElseThrow());
            case AUTHENTICATED -> new SimpleEntity(getGroup1().getId(), getGroup1().getName().orElseThrow());
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return a URI pointing to the provided project
     */
    protected static URI getProjectUri(long projectId) {
        return URI.create(getWebServerURI() + "/webclient/?show=project-" + projectId);
    }

    /**
     * @return a list of IDs of projects visible by the provided user type, and optionally belonging to the
     * provided experimenter and group
     */
    protected static List<Long> getProjectIds(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case AUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                        (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(2L);
                } else {
                    yield List.of();
                }
            }
            case UNAUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                        (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(1L);
                } else {
                    yield List.of();
                }
            }
            case ADMIN -> {
                List<Long> ids = new ArrayList<>(List.of(1L, 2L));

                if (experimenterId >= 0) {
                    if (experimenterId != getUser1().getId()) {
                        ids.removeAll(List.of(2L));
                    }
                    if (experimenterId != getPublicUser().getId()) {
                        ids.remove(1L);
                    }
                }

                if (groupId >= 0) {
                    if (groupId != getGroup1().getId()) {
                        ids.removeAll(List.of(2L));
                    }
                    if (groupId != getPublicGroup().getId()) {
                        ids.remove(1L);
                    }
                }

                yield ids;
            }
        };
    }

    /**
     * @return the project to consider with the provided user type
     */
    protected static SimpleServerEntity getProject(UserType userType) {
        return new SimpleServerEntity(
                EntityType.PROJECT,
                switch (userType) {
                    case UNAUTHENTICATED -> 1;
                    case AUTHENTICATED -> 2;
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }

    /**
     * @return the name of the project to consider with the provided user type
     */
    protected static String getProjectName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, AUTHENTICATED -> "project";
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the IDs of the children datasets of the project to consider with the provided user type, optionally belonging to the
     * provided experimenter and group
     */
    protected static List<Long> getProjectDatasetIds(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case UNAUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(1L);
                } else {
                    yield List.of();
                }
            }
            case AUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(3L, 4L);
                } else {
                    yield List.of();
                }
            }
            case ADMIN -> List.of();
        };
    }

    /**
     * @return the IDs of images belonging to the project to consider with the provided user type
     */
    protected static List<Long> getProjectImageIds(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
            case AUTHENTICATED -> List.of(19L, 20L);
            case ADMIN -> List.of();
        };
    }

    /**
     * @return the attributes of the project to consider with the provided user type
     */
    protected static List<String> getProjectAttributeValues(UserType userType) {
        return List.of(
                getProjectName(userType),
                String.valueOf(getProject(userType).id()),
                "-",
                getEntityOwner(userType).name(),
                getEntityGroup(userType).name(),
                String.valueOf(getProjectDatasetIds(userType, -1, -1).size())
        );
    }

    /**
     * @return a URI pointing to the provided dataset
     */
    protected static URI getDatasetUri(long datasetId) {
        return URI.create(getWebServerURI() + "/webclient/?show=dataset-" + datasetId);
    }

    /**
     * @return the IDs of the orphaned datasets visible by the provided user type, optionally belonging to the provided
     * experimenter and group
     */
    protected static List<Long> getOrphanedDatasetIds(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case AUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(5L, 6L);
                } else {
                    yield List.of();
                }
            }
            case UNAUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(2L);
                } else {
                    yield List.of();
                }
            }
            case ADMIN -> {
                List<Long> ids = new ArrayList<>(List.of(2L, 5L, 6L));

                if (experimenterId >= 0) {
                    if (experimenterId != getUser1().getId()) {
                        ids.removeAll(List.of(5L, 6L));
                    }
                    if (experimenterId != getPublicUser().getId()) {
                        ids.remove(2L);
                    }
                }

                if (groupId >= 0) {
                    if (groupId != getGroup1().getId()) {
                        ids.removeAll(List.of(5L, 6L));
                    }
                    if (groupId != getPublicGroup().getId()) {
                        ids.remove(2L);
                    }
                }

                yield ids;
            }
        };
    }

    /**
     * @return the dataset to consider with the provided user type
     */
    protected static SimpleServerEntity getDataset(UserType userType) {
        return new SimpleServerEntity(
                EntityType.DATASET,
                switch (userType) {
                    case UNAUTHENTICATED -> 1;
                    case AUTHENTICATED -> 4;
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }

    /**
     * @return the name of the dataset to consider with the provided user type
     */
    protected static String getDatasetName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> "dataset";
            case AUTHENTICATED -> "dataset2";
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the IDs of the image belonging to the dataset to consider with the provided user type
     */
    protected static List<Long> getDatasetImageIds(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case UNAUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
                } else {
                    yield List.of();
                }
            }
            case AUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(19L, 20L);
                } else {
                    yield List.of();
                }
            }
            case ADMIN -> List.of();
        };
    }

    /**
     * @return the attributes of the dataset to consider with the provided user type
     */
    protected static List<String> getDatasetAttributeValues(UserType userType) {
        return List.of(
                getDatasetName(userType),
                String.valueOf(getDataset(userType).id()),
                "-",
                getEntityOwner(userType).name(),
                getEntityGroup(userType).name(),
                String.valueOf(getDatasetImageIds(userType, -1, -1).size())
        );
    }

    /**
     * @return the annotations attached to the dataset to consider with the provided user type
     */
    protected static List<Annotation> getAnnotationsInDataset(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> List.of(
                    new CommentAnnotation(
                            new OmeroCommentAnnotation(
                                    1L,
                                    null,
                                    null,
                                    new OmeroAnnotationExperimenter(2L),
                                    new OmeroLink(new OmeroAnnotationExperimenter(2L)),
                                    "comment"
                            ),
                            List.of(new OmeroSimpleExperimenter(2L, "public", "access"))
                    ),
                    new FileAnnotation(
                            new OmeroFileAnnotation(
                                    2L,
                                    null,
                                    null,
                                    new OmeroAnnotationExperimenter(2L),
                                    new OmeroLink(new OmeroAnnotationExperimenter(2L)),
                                    new OmeroFile("analysis.csv", "text/csv", 15L)
                            ),
                            List.of(new OmeroSimpleExperimenter(2L, "public", "access"))
                    )
            );
            case AUTHENTICATED, ADMIN -> List.of();
        };
    }

    /**
     * @return search results when searching for "dataset" with the provided user type
     */
    protected static List<SearchResult> getSearchResultsOnDataset(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> List.of(
                    new SearchResult(
                            "dataset",
                            3,
                            "dataset1",
                            null,
                            null,
                            getGroup1().getName().orElseThrow(),
                            "/webclient/?show=dataset-3"
                    ),
                    new SearchResult(
                            "dataset",
                            4,
                            "dataset2",
                            null,
                            null,
                            getGroup1().getName().orElseThrow(),
                            "/webclient/?show=dataset-4"
                    ),
                    new SearchResult(
                            "dataset",
                            5,
                            "orphaned_dataset1",
                            null,
                            null,
                            getGroup1().getName().orElseThrow(),
                            "/webclient/?show=dataset-5"
                    ),
                    new SearchResult(
                            "dataset",
                            6,
                            "orphaned_dataset2",
                            null,
                            null,
                            getGroup1().getName().orElseThrow(),
                            "/webclient/?show=dataset-6"
                    )
            );
            case UNAUTHENTICATED -> List.of(
                    new SearchResult(
                            "dataset",
                            1,
                            "dataset",
                            null,
                            null,
                            getPublicGroup().getName().orElseThrow(),
                            "/webclient/?show=dataset-1"
                    ),
                    new SearchResult(
                            "dataset",
                            2,
                            "orphaned_dataset",
                            null,
                            null,
                            getPublicGroup().getName().orElseThrow(),
                            "/webclient/?show=dataset-2"
                    )
            );
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return a URI pointing to the provided image
     */
    protected static URI getImageUri(long imageId) {
        return URI.create(getWebServerURI() + "/webclient/?show=image-" + imageId);
    }

    /**
     * @return the IDs of the orphaned images visible by the provided user type, optionally filtered to the provided
     * experimenter and group
     */
    protected static List<Long> getOrphanedImageIds(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case AUTHENTICATED -> {
                List<Long> ids = new ArrayList<>(List.of(53L, 54L, 55L, 56L, 57L, 58L));

                if (experimenterId >= 0) {
                    if (experimenterId == getUser().getId()) {
                        ids.removeAll(List.of(53L, 54L, 55L, 56L, 57L));
                    } else if (experimenterId == getUser2().getId()) {
                        ids.remove(58L);
                    } else {
                        ids.clear();
                    }
                }

                if (groupId >= 0) {
                    if (groupId == getGroup1().getId()) {
                        ids.removeAll(List.of(53L, 54L, 55L, 56L, 57L));
                    } else if (groupId == getGroup2().getId()) {
                        ids.remove(58L);
                    } else {
                        ids.clear();
                    }
                }

                yield ids;
            }
            case UNAUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(8L);
                } else {
                    yield List.of();
                }
            }
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the image to consider with the provided user type
     */
    protected static SimpleServerEntity getImage(UserType userType) {
        return new SimpleServerEntity(
                EntityType.IMAGE,
                switch (userType) {
                    case UNAUTHENTICATED -> 1;
                    case AUTHENTICATED -> 20;
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }

    /**
     * @return the ID of the RGB image with the provided user type
     */
    protected static SimpleServerEntity getRgbImage(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 1);
            case AUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 19);
            default -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the metadata of the RGB image with the provided user type
     */
    protected static ImageServerMetadata getRgbImageMetadata() {
        return new ImageServerMetadata.Builder()
                .name("rgb.tiff")
                .pixelType(PixelType.UINT8)
                .width(256)
                .height(256)
                .rgb(true)
                .channels(List.of(
                        ImageChannel.getInstance("0", ColorTools.RED),
                        ImageChannel.getInstance("1", ColorTools.GREEN),
                        ImageChannel.getInstance("2", ColorTools.BLUE)
                ))
                .pixelSizeMicrons(1, 1)
                .build();
    }

    /**
     * @return a list of parents (including the image itself) of the RGB image with the provided user type
     */
    protected static List<SimpleServerEntity> getParentOfRgbImage(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> List.of(
                    new SimpleServerEntity(EntityType.IMAGE, 1),
                    new SimpleServerEntity(EntityType.DATASET, 1),
                    new SimpleServerEntity(EntityType.PROJECT, 1)
            );
            case AUTHENTICATED -> List.of(
                    new SimpleServerEntity(EntityType.IMAGE, 19),
                    new SimpleServerEntity(EntityType.DATASET, 4),
                    new SimpleServerEntity(EntityType.PROJECT, 2)
            );
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the pixels of the RGB image
     */
    protected static BufferedImage getRgbImage() {
        try (var server = ImageServers.buildServer(Objects.requireNonNull(OmeroServer.class.getResource("/omero-server/images/rgb.tiff")).getFile())) {
            return server.readRegion(1, 0, 0, server.getWidth(), server.getHeight());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the pixels of the UINT8 image
     */
    protected static BufferedImage getUint8Image() {
        try (var server = ImageServers.buildServer(Objects.requireNonNull(OmeroServer.class.getResource("/omero-server/images/uint8.tiff")).getFile())) {
            return server.readRegion(1, 0, 0, server.getWidth(), server.getHeight());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the pixels of the UINT16 image
     */
    protected static BufferedImage getUint16Image() {
        try (var server = ImageServers.buildServer(Objects.requireNonNull(OmeroServer.class.getResource("/omero-server/images/uint16.tiff")).getFile())) {
            return server.readRegion(1, 0, 0, server.getWidth(), server.getHeight());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the pixels of the INT16 image
     */
    protected static BufferedImage getInt16Image() {
        try (var server = ImageServers.buildServer(Objects.requireNonNull(OmeroServer.class.getResource("/omero-server/images/int16.tiff")).getFile())) {
            return server.readRegion(1, 0, 0, server.getWidth(), server.getHeight());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the pixels of the INT32 image
     */
    protected static BufferedImage getInt32Image() {
        try (var server = ImageServers.buildServer(Objects.requireNonNull(OmeroServer.class.getResource("/omero-server/images/int32.tiff")).getFile())) {
            return server.readRegion(1, 0, 0, server.getWidth(), server.getHeight());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the ID of the FLOAT32 image with the provided user type
     */
    protected static SimpleServerEntity getFloat32Image(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 6);
            case AUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 20);
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the image settings of the FLOAT32 image
     */
    protected static ImageSettings getFloat32ImageSettings() {
        return new ImageSettings(
                "float32.tiff",
                List.of(
                        new ChannelSettings("0", 0, 211, Integer.parseInt("FF0000", 16)),
                        new ChannelSettings("1", 0, 248, Integer.parseInt("00FF00", 16)),
                        new ChannelSettings("2", 0, 184, Integer.parseInt("0000FF", 16))
                )
        );
    }

    /**
     * @return the pixels of the FLOAT32 image
     */
    protected static BufferedImage getFloat32Image() {
        try (var server = ImageServers.buildServer(Objects.requireNonNull(OmeroServer.class.getResource("/omero-server/images/float32.tiff")).getFile())) {
            return server.readRegion(1, 0, 0, server.getWidth(), server.getHeight());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the pixels of the FLOAT64 image
     */
    protected static BufferedImage getFloat64Image() {
        try (var server = ImageServers.buildServer(Objects.requireNonNull(OmeroServer.class.getResource("/omero-server/images/float64.tiff")).getFile())) {
            return server.readRegion(1, 0, 0, server.getWidth(), server.getHeight());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the ID of the complex image with the provided user type
     */
    protected static SimpleServerEntity getComplexImage(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 8);
            case AUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 58);
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the metadata of the complex image
     */
    protected static ImageServerMetadata getComplexImageMetadata() {
        return new ImageServerMetadata.Builder()
                .name("complex.tiff")
                .pixelType(PixelType.FLOAT32)
                .width(256)
                .height(256)
                .sizeZ(10)
                .sizeT(3)
                .channels(List.of(ImageChannel.getInstance("0", ColorTools.packRGB(128, 128, 128))))
                .pixelSizeMicrons(2.675500000484335, 2.675500000484335)
                .zSpacingMicrons(3.947368)
                .build();
    }

    /**
     * @return an image that the provided user type can add shapes to
     */
    protected static SimpleServerEntity getAnnotableImage(UserType userType) {
        return getRgbImage(userType);
    }

    /**
     * @return an image that the provided user type can modify (e.g. change image name, channel settings)
     */
    protected static SimpleServerEntity getModifiableImage(UserType userType) {
        return getComplexImage(userType);
    }

    /**
     * @return the original name of the image that can be modified (e.g. change image name, channel settings)
     */
    protected static String getModifiableImageName() {
        return "complex.tiff";
    }

    /**
     * @return the original channel settings of the image that can be modified (e.g. change image name, channel settings)
     */
    protected static ChannelSettings getModifiableImageChannelSettings() {
        return new ChannelSettings("0", 0, 240, Integer.parseInt("808080", 16));
    }

    /**
     * @return the ID and full name of the experimenter owning the image to consider with the provided user type
     */
    protected static SimpleEntity getImageOwner(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleEntity(getPublicUser().getId(), getPublicUser().getFullName());
            case AUTHENTICATED -> new SimpleEntity(getUser1().getId(), getUser1().getFullName());
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the ID and name of the group owning the image to consider with the provided user type
     */
    protected static SimpleEntity getImageGroup(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleEntity(getPublicGroup().getId(), getPublicGroup().getName().orElseThrow());
            case AUTHENTICATED -> new SimpleEntity(getGroup1().getId(), getGroup1().getName().orElseThrow());
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the name of the image to consider with the provided user type
     */
    protected static String getImageName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> "rgb.tiff";
            case AUTHENTICATED -> "float32.tiff";
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the attributes of the image to consider with the provided user type
     */
    protected static List<String> getImageAttributeValues(UserType userType) {
        return List.of(
                getImageName(userType),
                String.valueOf(getImage(userType).id()),
                getImageOwner(userType).name(),
                getImageGroup(userType).name(),
                "-",
                "256 px",
                "256 px",
                switch (userType) {
                    case UNAUTHENTICATED -> "0.2 MiB";
                    case AUTHENTICATED -> "0.8 MiB";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                },
                "1",
                "3",
                "1",
                "1.0 µm",
                "1.0 µm",
                "-",
                switch (userType) {
                    case UNAUTHENTICATED -> "UINT8";
                    case AUTHENTICATED -> "FLOAT32";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }

    /**
     * @return a URI pointing to the provided screen
     */
    protected static URI getScreenUri(long screenId) {
        return URI.create(getWebServerURI() + "/webclient/?show=screen-" + screenId);
    }

    /**
     * @return the IDs of the screens visible by the provided user type, optionally filtered to the provided experimenter and group
     */
    protected static List<Long> getScreenIds(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case AUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(2L, 3L);
                } else {
                    yield List.of();
                }
            }
            case UNAUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(1L);
                } else {
                    yield List.of();
                }
            }
            case ADMIN -> {
                List<Long> ids = new ArrayList<>(List.of(1L, 2L, 3L));

                if (experimenterId >= 0) {
                    if (experimenterId != getUser1().getId()) {
                        ids.removeAll(List.of(2L, 3L));
                    }
                    if (experimenterId != getPublicUser().getId()) {
                        ids.remove(1L);
                    }
                }

                if (groupId >= 0) {
                    if (groupId != getGroup1().getId()) {
                        ids.removeAll(List.of(2L, 3L));
                    }
                    if (groupId != getPublicGroup().getId()) {
                        ids.remove(1L);
                    }
                }

                yield ids;
            }
        };
    }

    /**
     * @return the screen to consider with the provided user type
     */
    protected static SimpleServerEntity getScreen(UserType userType) {
        return new SimpleServerEntity(
                EntityType.SCREEN,
                switch (userType) {
                    case UNAUTHENTICATED -> 1;
                    case AUTHENTICATED -> 3;
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }

    /**
     * @return the name of the screen to consider with the provided user type
     */
    protected static String getScreenName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> "screen";
            case AUTHENTICATED -> "screen2";
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the IDs of the plates belonging to the screen to consider with the provided user type, optionally filtered to the
     * provided experimenter and group
     */
    protected static List<Long> getScreenPlateIds(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case UNAUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(1L);
                } else {
                    yield List.of();
                }
            }
            case AUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(4L);
                } else {
                    yield List.of();
                }
            }
            case ADMIN -> List.of();
        };
    }

    /**
     * @return the attributes of the screen to consider with the provided user type
     */
    protected static List<String> getScreenAttributeValues(UserType userType) {
        return List.of(
                getScreenName(userType),
                String.valueOf(getScreen(userType).id()),
                "-",
                getEntityOwner(userType).name(),
                getEntityGroup(userType).name(),
                String.valueOf(getScreenPlateIds(userType, -1, -1).size())
        );
    }

    /**
     * @return a URI pointing to the provided plate
     */
    protected static URI getPlateUri(long plateId) {
        return URI.create(getWebServerURI() + "/webclient/?show=plate-" + plateId);
    }

    /**
     * @return the ID of the orphaned plates visible by the provided user type, optionally filtered to the
     * provided experimenter and group
     */
    protected static List<Long> getOrphanedPlateIds(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case AUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(5L, 6L);
                } else {
                    yield List.of();
                }
            }
            case UNAUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(2L);
                } else {
                    yield List.of();
                }
            }
            case ADMIN -> {
                List<Long> ids = new ArrayList<>(List.of(2L, 5L, 6L));

                if (experimenterId >= 0) {
                    if (experimenterId != getUser1().getId()) {
                        ids.removeAll(List.of(5L, 6L));
                    }
                    if (experimenterId != getPublicUser().getId()) {
                        ids.remove(2L);
                    }
                }

                if (groupId >= 0) {
                    if (groupId != getGroup1().getId()) {
                        ids.removeAll(List.of(5L, 6L));
                    }
                    if (groupId != getPublicGroup().getId()) {
                        ids.remove(2L);
                    }
                }

                yield ids;
            }
        };
    }

    /**
     * @return the plate to consider with the provided user type
     */
    protected static SimpleServerEntity getPlate(UserType userType) {
        return new SimpleServerEntity(
                EntityType.PLATE,
                switch (userType) {
                    case UNAUTHENTICATED -> 1;
                    case AUTHENTICATED -> 4;
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }

    /**
     * @return the name of the plate to consider with the provided user type
     */
    protected static String getPlateName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> "plate";
            case AUTHENTICATED -> "plate-plate_acquisition-well.xml";
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the IDs of the plate acquisitions belonging to the plate to consider with the provided user type, optionally
     * filtered to the provided experimenter and group
     */
    protected static List<Long> getPlatePlateAcquisitionIds(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> List.of();
            case AUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(1L, 2L);
                } else {
                    yield List.of();
                }
            }
        };
    }

    /**
     * @return the number of non-empty wells belonging to the plate to consider with the provided user type, optionally
     * filtered by the provided experimenter and group. Only the number can be retrieved because the well IDs are not
     * predictable
     */
    protected static int getNumberOfPlateNonEmptyWells(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case UNAUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield 4;
                } else {
                    yield 0;
                }
            }
            case AUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield 1;
                } else {
                    yield 0;
                }
            }
            case ADMIN -> 0;
        };
    }

    /**
     * @return the number of wells belonging to the plate to consider with the provided user type, optionally
     * filtered by the provided experimenter and group. Only the number can be retrieved because the well IDs are not
     * predictable
     */
    protected static int getNumberOfPlateWells(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case UNAUTHENTICATED, AUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield 4;
                } else {
                    yield 0;
                }
            }
            case ADMIN -> 0;
        };
    }

    /**
     * @return the attributes of the plate to consider with the provided user type
     */
    protected static List<String> getPlateAttributeValues(UserType userType) {
        return List.of(
                getPlateName(userType),
                String.valueOf(getPlate(userType).id()),
                getEntityOwner(userType).name(),
                getEntityGroup(userType).name(),
                switch (userType) {
                    case UNAUTHENTICATED, AUTHENTICATED -> "3";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                },
                switch (userType) {
                    case UNAUTHENTICATED, AUTHENTICATED -> "3";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }


    /**
     * @return a URI pointing to the provided plate acquisition
     */
    protected static URI getPlateAcquisitionUri(long plateAcquisitionId) {
        return URI.create(getWebServerURI() + "/webclient/?show=run-" + plateAcquisitionId);
    }

    /**
     * @return the plate acquisition to consider with the provided user type
     */
    protected static SimpleServerEntity getPlateAcquisition(UserType userType) {
        return new SimpleServerEntity(
                EntityType.PLATE_ACQUISITION,
                switch (userType) {
                    case UNAUTHENTICATED, ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                    case AUTHENTICATED -> 1;
                }
        );
    }

    /**
     * @return the name of the plate acquisition to consider with the provided user type
     */
    protected static String getPlateAcquisitionName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
            case AUTHENTICATED -> null;
        };
    }

    /**
     * @return the number of wells belonging to the plate acquisition to consider with the provided user type, optionally
     * filtered by the provided experimenter and group. Only the number can be retrieved because the well IDs are not
     * predictable
     */
    protected static int getNumberOfPlateAcquisitionWell(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> 0;
            case AUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield 4;
                } else {
                    yield 0;
                }
            }
        };
    }

    /**
     * @return the attributes of the plate acquisition to consider with the provided user type
     */
    protected static List<String> getPlateAcquisitionAttributeValues(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
            case AUTHENTICATED -> List.of(
                    "Run 1",
                    String.valueOf(getPlateAcquisition(userType).id()),
                    getEntityOwner(userType).name(),
                    getEntityGroup(userType).name(),
                    "2010-02-23 12:50:30"
            );
        };
    }

    /**
     * @return the min well sample index of the plate acquisition to consider with the provided user type
     */
    protected static int getPlateAcquisitionMinWellSampleIndex(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
            case AUTHENTICATED -> 1;
        };
    }

    /**
     * @return the max well sample index of the plate acquisition to consider with the provided user type
     */
    protected static int getPlateAcquisitionMaxWellSampleIndex(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
            case AUTHENTICATED -> 1;
        };
    }


    /**
     * @return a URI pointing to the provided well
     */
    protected static URI getWellUri(long wellId) {
        return URI.create(getWebServerURI() + "/webclient/?show=well-" + wellId);
    }

    /**
     * @return the well to consider with the provided user type
     */
    protected static SimpleServerEntity getWell(UserType userType) {
        return new SimpleServerEntity(
                EntityType.WELL,
                switch (userType) {
                    case UNAUTHENTICATED -> 4;
                    case AUTHENTICATED -> 15;
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }

    /**
     * @return the name of the well to consider with the provided user type
     */
    protected static String getWellName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, AUTHENTICATED -> null;
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    /**
     * @return the IDs of the image belonging to the well to consider with the provided user type and belonging to a
     * plate (not plate acquisition), optionally filtered to the provided experimenter and group
     */
    protected static List<Long> getWellImageIds(UserType userType, long experimenterId, long groupId) {
        return switch (userType) {
            case UNAUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(9L);
                } else {
                    yield List.of();
                }
            }
            case AUTHENTICATED -> {
                if (
                        (experimenterId < 0 || experimenterId == getEntityOwner(userType).id()) &&
                                (groupId < 0 || groupId == getEntityGroup(userType).id())
                ) {
                    yield List.of(34L, 35L);
                } else {
                    yield List.of();
                }
            }
            case ADMIN -> List.of();
        };
    }

    /**
     * @return the IDs of the image belonging to the well to consider with the provided user type
     */
    protected static List<Long> getWellAllImageIds(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> List.of(9L);
            case AUTHENTICATED -> List.of(31L, 32L, 33L, 34L, 35L);
            case ADMIN -> List.of();
        };
    }

    /**
     * @return the attributes of the well to consider with the provided user type
     */
    protected static List<String> getWellAttributeValues(UserType userType) {
        return List.of(
                switch (userType) {
                    case UNAUTHENTICATED -> "A2";
                    case AUTHENTICATED -> "C2";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                },
                String.valueOf(getWell(userType).id()),
                getEntityOwner(userType).name(),
                getEntityGroup(userType).name(),
                switch (userType) {
                    case UNAUTHENTICATED, AUTHENTICATED -> "1";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                },
                switch (userType) {
                    case UNAUTHENTICATED -> "0";
                    case AUTHENTICATED -> "2";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }

    private static void logCommandResult(Container.ExecResult result) {
        if (!result.getStdout().isBlank()) {
            logger.debug("Setting up OMERO server: {}", result.getStdout());
        }

        if (!result.getStderr().isBlank()) {
            logger.warn("Setting up OMERO server: {}", result.getStderr());
        }
    }

    private static ExperimenterGroup getSystemGroup() {
        return new ExperimenterGroup(
                getSystemOmeroGroup(),
                List.of(getRootUser(), getAdminUser())
        );
    }

    private static ExperimenterGroup getUserGroup() {
        return new ExperimenterGroup(
                getUserOmeroGroup(),
                List.of()
        );
    }

    private static ExperimenterGroup getGuestGroup() {
        return new ExperimenterGroup(
                getGuestOmeroGroup(),
                List.of()
        );
    }

    private static ExperimenterGroup getPublicGroup() {
        return new ExperimenterGroup(
                getPublicOmeroGroup(),
                List.of(getPublicUser())
        );
    }

    private static ExperimenterGroup getGroup1() {
        return new ExperimenterGroup(
                getGroup1OmeroGroup(),
                List.of(getUser(), getUser1())
        );
    }

    private static ExperimenterGroup getGroup2() {
        return new ExperimenterGroup(
                getGroup2OmeroGroup(),
                List.of(getUser(), getUser2())
        );
    }

    private static ExperimenterGroup getGroup3() {
        return new ExperimenterGroup(
                getGroup3OmeroGroup(),
                List.of(getUser(), getUser3())
        );
    }

    private static OmeroExperimenterGroup getSystemOmeroGroup() {
        return new OmeroExperimenterGroup(
                OmeroExperimenterGroup.TYPE,
                0L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, false, false)
                ),
                "system",
                getWebServerURI() + "/api/v0/m/experimentergroups/0/experimenters/"
        );
    }

    private static OmeroExperimenterGroup getUserOmeroGroup() {
        return new OmeroExperimenterGroup(
                OmeroExperimenterGroup.TYPE,
                1L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, false)
                ),
                "user",
                getWebServerURI() + "/api/v0/m/experimentergroups/1/experimenters/"
        );
    }

    private static OmeroExperimenterGroup getGuestOmeroGroup() {
        return new OmeroExperimenterGroup(
                OmeroExperimenterGroup.TYPE,
                2L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, false, false)
                ),
                "guest",
                getWebServerURI() + "/api/v0/m/experimentergroups/2/experimenters/"
        );
    }

    private static OmeroExperimenterGroup getPublicOmeroGroup() {
        return new OmeroExperimenterGroup(
                OmeroExperimenterGroup.TYPE,
                3L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, false)
                ),
                "public-data",
                getWebServerURI() + "/api/v0/m/experimentergroups/3/experimenters/"
        );
    }

    private static OmeroExperimenterGroup getGroup1OmeroGroup() {
        return new OmeroExperimenterGroup(
                OmeroExperimenterGroup.TYPE,
                4L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                ),
                "group1",
                getWebServerURI() + "/api/v0/m/experimentergroups/4/experimenters/"
        );
    }

    private static OmeroExperimenterGroup getGroup2OmeroGroup() {
        return new OmeroExperimenterGroup(
                OmeroExperimenterGroup.TYPE,
                5L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, false)
                ),
                "group2",
                getWebServerURI() + "/api/v0/m/experimentergroups/5/experimenters/"
        );
    }

    private static OmeroExperimenterGroup getGroup3OmeroGroup() {
        return new OmeroExperimenterGroup(
                OmeroExperimenterGroup.TYPE,
                6L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, false, false)
                ),
                "group3",
                getWebServerURI() + "/api/v0/m/experimentergroups/6/experimenters/"
        );
    }

    private static Experimenter getRootUser() {
        return new Experimenter(getOmeroRootUser());
    }

    private static Experimenter getGuestUser() {
        return new Experimenter(getOmeroGuestUser());
    }

    private static Experimenter getAdminUser() {
        return new Experimenter(getOmeroAdminUser());
    }

    private static Experimenter getPublicUser() {
        return new Experimenter(getOmeroPublicUser());
    }

    private static Experimenter getUser1() {
        return new Experimenter(getOmeroUser1());
    }

    private static Experimenter getUser2() {
        return new Experimenter(getOmeroUser2());
    }

    private static Experimenter getUser3() {
        return new Experimenter(getOmeroUser3());
    }

    private static Experimenter getUser() {
        return new Experimenter(getOmeroUser());
    }

    private static OmeroExperimenter getOmeroRootUser() {
        return new OmeroExperimenter(
                OmeroExperimenter.TYPE,
                0L,
                "root",
                null,
                "root"
        );
    }

    private static OmeroExperimenter getOmeroGuestUser() {
        return new OmeroExperimenter(
                OmeroExperimenter.TYPE,
                1L,
                "Guest",
                null,
                "Account"
        );
    }

    private static OmeroExperimenter getOmeroAdminUser() {
        return new OmeroExperimenter(
                OmeroExperimenter.TYPE,
                2L,
                "admin",
                null,
                "admin"
        );
    }

    private static OmeroExperimenter getOmeroPublicUser() {
        return new OmeroExperimenter(
                OmeroExperimenter.TYPE,
                3L,
                "public",
                null,
                "access"
        );
    }

    private static OmeroExperimenter getOmeroUser1() {
        return new OmeroExperimenter(
                OmeroExperimenter.TYPE,
                4L,
                "user1",
                null,
                "user1"
        );
    }

    private static OmeroExperimenter getOmeroUser2() {
        return new OmeroExperimenter(
                OmeroExperimenter.TYPE,
                5L,
                "user2",
                null,
                "user2"
        );
    }

    private static OmeroExperimenter getOmeroUser3() {
        return new OmeroExperimenter(
                OmeroExperimenter.TYPE,
                6L,
                "user3",
                null,
                "user3"
        );
    }

    private static OmeroExperimenter getOmeroUser() {
        return new OmeroExperimenter(
                OmeroExperimenter.TYPE,
                7L,
                "user",
                null,
                "user"
        );
    }

    private static int getMsPixelBufferApiPort() {
        return omeroWeb == null ? MS_PIXEL_BUFFER_PORT : omeroWeb.getMappedPort(MS_PIXEL_BUFFER_PORT);
    }
}
