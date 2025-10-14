package qupath.ext.omero;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Image;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Well;
import qupath.ext.omero.core.apis.webclient.EntityType;
import qupath.ext.omero.core.apis.webclient.SimpleServerEntity;
import qupath.ext.omero.core.apis.webclient.annotations.Annotation;
import qupath.ext.omero.core.apis.webclient.search.SearchResult;
import qupath.ext.omero.core.apis.webclient.search.SearchResultWithParentInfo;
import qupath.ext.omero.core.pixelapis.mspixelbuffer.MsPixelBufferApi;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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
    private static final String analysisFileId;
    private enum ImageType {
        RGB,
        UINT8,
        UINT16,
        INT16,
        INT32,
        FLOAT32,
        FLOAT64,
        COMPLEX
    }
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
            analysisFileId = "64";
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

                String[] logs = omeroServerSetupResult.getStdout().split("\n");
                analysisFileId = logs[logs.length-1].split(":")[1];

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

    protected static String getWebServerURI() {
        return omeroWeb == null ?
                "http://localhost:" + OMERO_WEB_PORT :
                "http://" + omeroWeb.getHost() + ":" + omeroWeb.getMappedPort(OMERO_WEB_PORT);
    }

    protected static Credentials getCredentials(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new Credentials();
            case AUTHENTICATED, ADMIN -> new Credentials(
                    getUsername(userType),
                    getPassword(userType).toCharArray()
            );
        };
    }

    protected static String getServerAddress() {
        return "omero-server";
    }

    protected static int getServerPort() {
        return OMERO_SERVER_PORT;
    }

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

    protected static String getUsername(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> "public";
            case AUTHENTICATED -> "user";
            case ADMIN -> "admin";
        };
    }

    protected static String getPassword(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> "password_user";
            case UNAUTHENTICATED -> "password_public";
            case ADMIN -> "password_admin";
        };
    }

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

    protected static ExperimenterGroup getDefaultGroup(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> getGroup1();
            case UNAUTHENTICATED -> getPublicGroup();
            case ADMIN -> getSystemGroup();
        };
    }

    protected static ExperimenterGroup getGroupOfEntity(ServerEntity serverEntity) {
        return switch (serverEntity) {
            case Image image -> {
                if (image.getId() < 19) {
                    yield getPublicGroup();
                } else if (image.getId() < 53 || image.getId() == 58) {
                    yield getGroup1();
                } else if (image.getId() < 58) {
                    yield getGroup2();
                } else {
                    throw new IllegalArgumentException(String.format("Unknown entity %s", serverEntity));
                }
            }
            case Project project -> switch ((int) project.getId()) {
                case 1 -> getPublicGroup();
                case 2 -> getGroup1();
                default -> throw new IllegalArgumentException(String.format("Unknown entity %s", serverEntity));
            };
            case Dataset dataset -> switch ((int) dataset.getId()) {
                case 1, 2 -> getPublicGroup();
                case 3, 4, 5, 6 -> getGroup1();
                default -> throw new IllegalArgumentException(String.format("Unknown entity %s", serverEntity));
            };
            case Screen screen -> switch ((int) screen.getId()) {
                case 1 -> getPublicGroup();
                case 2, 3 -> getGroup1();
                default -> throw new IllegalArgumentException(String.format("Unknown entity %s", serverEntity));
            };
            case Plate plate -> switch ((int) plate.getId()) {
                case 1, 2 -> getPublicGroup();
                case 3, 4, 5, 6 -> getGroup1();
                default -> throw new IllegalArgumentException(String.format("Unknown entity %s", serverEntity));
            };
            case PlateAcquisition plateAcquisition -> switch ((int) plateAcquisition.getId()) {
                case 1, 2 -> getGroup1();
                default -> throw new IllegalArgumentException(String.format("Unknown entity %s", serverEntity));
            };
            case Well well -> {
                if (well.getId() < 9) {
                    yield getPublicGroup();
                } else if (well.getId() < 25) {
                    yield getGroup1();
                } else {
                    throw new IllegalArgumentException(String.format("Unknown entity %s", serverEntity));
                }
            }
            case null, default -> throw new IllegalArgumentException(String.format("Unknown entity %s", serverEntity));
        };
    }

    protected static List<ExperimenterGroup> getGroupsOwnedByUser(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> List.of();
            case AUTHENTICATED -> List.of(getGroup3());
        };
    }

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

    protected static Experimenter getConnectedExperimenter(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> getUser();
            case UNAUTHENTICATED -> getPublicUser();
            case ADMIN -> getAdminUser();
        };
    }

    protected static SimpleEntity getEntityOwner(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleEntity(getPublicUser().getId(), getPublicUser().getFullName());
            case AUTHENTICATED -> new SimpleEntity(getUser1().getId(), getUser1().getFullName());
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static SimpleEntity getEntityGroup(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleEntity(getPublicGroup().getId(), getPublicGroup().getName().orElseThrow());
            case AUTHENTICATED -> new SimpleEntity(getGroup1().getId(), getGroup1().getName().orElseThrow());
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static URI getProjectUri(long projectId) {
        return URI.create(getWebServerURI() + "/webclient/?show=project-" + projectId);
    }

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
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

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

    protected static String getProjectName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, AUTHENTICATED -> "project";
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static List<Long> getProjectDatasetIds(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> List.of(1L);
            case AUTHENTICATED -> List.of(3L, 4L);
            case ADMIN -> List.of();
        };
    }

    protected static List<Long> getProjectImageIds(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
            case AUTHENTICATED -> List.of(19L, 20L);
            case ADMIN -> List.of();
        };
    }

    protected static List<String> getProjectAttributeValues(UserType userType) {
        return List.of(
                getProjectName(userType),
                String.valueOf(getProject(userType).id()),
                "-",
                getEntityOwner(userType).name(),
                getEntityGroup(userType).name(),
                String.valueOf(getProjectDatasetIds(userType).size())
        );
    }

    protected static URI getDatasetUri(long datasetId) {
        return URI.create(getWebServerURI() + "/webclient/?show=dataset-" + datasetId);
    }

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
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

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

    protected static String getDatasetName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> "dataset";
            case AUTHENTICATED -> "dataset2";
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static List<Long> getDatasetImageIds(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
            case AUTHENTICATED -> List.of(19L, 20L);
            case ADMIN -> List.of();
        };
    }

    protected static List<String> getDatasetAttributeValues(UserType userType) {
        return List.of(
                getDatasetName(userType),
                String.valueOf(getDataset(userType).id()),
                "-",
                getEntityOwner(userType).name(),
                getEntityGroup(userType).name(),
                String.valueOf(getDatasetImageIds(userType).size())
        );
    }

    protected static URI getImageUri(long imageId) {
        return URI.create(getWebServerURI() + "/webclient/?show=image-" + imageId);
    }

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

    protected static SimpleServerEntity getRgbImage(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 1);
            case AUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 19);
            default -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

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

    protected static List<SimpleServerEntity> getParentOfRgbImage(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> List.of(
                    new SimpleServerEntity(EntityType.DATASET, 1),
                    new SimpleServerEntity(EntityType.PROJECT, 1)
            );
            case AUTHENTICATED -> List.of(
                    new SimpleServerEntity(EntityType.DATASET, 4),
                    new SimpleServerEntity(EntityType.PROJECT, 2)
            );
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static double getRgbImageRedChannelMean() {
        return 5.414;
    }

    protected static double getRgbImageRedChannelStdDev() {
        return 18.447;
    }

    protected static SimpleServerEntity getUint8Image(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 2);
            case AUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 53);
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static double getUint8ImageRedChannelMean() {
        return 7.134;
    }

    protected static double getUint8ImageRedChannelStdDev() {
        return 24.279;
    }

    protected static SimpleServerEntity getUint16Image(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 3);
            case AUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 54);
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static double getUint16ImageRedChannelMean() {
        return 4.295;
    }

    protected static double getUint16ImageRedChannelStdDev() {
        return 14.650;
    }

    protected static SimpleServerEntity getInt16Image(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 4);
            case AUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 55);
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static double getInt16ImageRedChannelMean() {
        return 6.730;
    }

    protected static double getInt16ImageRedChannelStdDev() {
        return 22.913;
    }

    protected static SimpleServerEntity getInt32Image(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 5);
            case AUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 56);
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static double getInt32ImageRedChannelMean() {
        return 4.574;
    }

    protected static double getInt32ImageRedChannelStdDev() {
        return 15.597;
    }

    protected static SimpleServerEntity getFloat32Image(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 6);
            case AUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 20);
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

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

    protected static double getFloat32ImageRedChannelMean() {
        return 8.429;
    }

    protected static double getFloat32ImageRedChannelStdDev() {
        return 28.605;
    }

    protected static SimpleServerEntity getFloat64Image(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 7);
            case AUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 57);
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static double getFloat64ImageRedChannelMean() {
        return 7.071;
    }

    protected static double getFloat64ImageRedChannelStdDev() {
        return 23.995;
    }

    protected static SimpleServerEntity getComplexImage(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 8);
            case AUTHENTICATED -> new SimpleServerEntity(EntityType.IMAGE, 58);
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

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

    protected static double getComplexImageRedChannelMean() {
        return 0.0;
    }

    protected static double getComplexImageRedChannelStdDev() {
        return 0.0;
    }

    protected static SimpleServerEntity getAnnotableImage(UserType userType) {
        return getRgbImage(userType);
    }

    protected static SimpleServerEntity getModifiableImage(UserType userType) {
        return getComplexImage(userType);
    }

    protected static String getModifiableImageName() {
        return "complex.tiff";
    }

    protected static ChannelSettings getModifiableImageChannelSettings() {
        return new ChannelSettings("0", 0, 240, Integer.parseInt("808080", 16));
    }

    protected static SimpleEntity getImageOwner(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleEntity(getPublicUser().getId(), getPublicUser().getFullName());
            case AUTHENTICATED -> new SimpleEntity(getUser1().getId(), getUser1().getFullName());
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static SimpleEntity getImageGroup(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> new SimpleEntity(getPublicGroup().getId(), getPublicGroup().getName().orElseThrow());
            case AUTHENTICATED -> new SimpleEntity(getGroup1().getId(), getGroup1().getName().orElseThrow());
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static String getImageName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> "rgb.tiff";
            case AUTHENTICATED -> "float32.tiff";
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

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
                    case UNAUTHENTICATED -> "0.2 MB";
                    case AUTHENTICATED -> "7.5 MB";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                },
                "1",
                "3",
                "1",
                "1 µm",
                "1 µm",
                "-",
                switch (userType) {
                    case UNAUTHENTICATED -> "uint8";
                    case AUTHENTICATED -> "float";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }

    protected static URI getScreenUri(long screenId) {
        return URI.create(getWebServerURI() + "/webclient/?show=screen-" + screenId);
    }

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
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

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

    protected static String getScreenName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> "screen";
            case AUTHENTICATED -> "screen2";
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static List<Long> getScreenPlateIds(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> List.of(1L);
            case AUTHENTICATED -> List.of(4L);
            case ADMIN -> List.of();
        };
    }

    protected static List<String> getScreenAttributeValues(UserType userType) {
        return List.of(
                getScreenName(userType),
                String.valueOf(getScreen(userType).id()),
                "-",
                getEntityOwner(userType).name(),
                getEntityGroup(userType).name(),
                String.valueOf(getScreenPlateIds(userType).size())
        );
    }

    protected static URI getPlateUri(long plateId) {
        return URI.create(getWebServerURI() + "/webclient/?show=plate-" + plateId);
    }

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
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

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

    protected static String getPlateName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> "plate";
            case AUTHENTICATED -> "plate-plate_acquisition-well.xml";
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static List<Long> getPlatePlateAcquisitionIds(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> List.of();
            case AUTHENTICATED -> List.of(1L, 2L);
        };
    }

    protected static List<Long> getPlateWellIds(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> List.of(1L, 2L, 3L, 4L);
            case AUTHENTICATED -> List.of(13L, 14L, 15L, 16L);
            case ADMIN -> List.of();
        };
    }

    protected static List<String> getPlateAttributeValues(UserType userType) {
        return List.of(
                getPlateName(userType),
                String.valueOf(getPlate(userType).id()),
                "-",
                getEntityOwner(userType).name(),
                getEntityGroup(userType).name(),
                switch (userType) {
                    case UNAUTHENTICATED -> "3";
                    case AUTHENTICATED -> "2";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                },
                switch (userType) {
                    case UNAUTHENTICATED -> "3";
                    case AUTHENTICATED -> "2";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }

    protected static URI getPlateAcquisitionUri(long plateAcquisitionId) {
        return URI.create(getWebServerURI() + "/webclient/?show=run-" + plateAcquisitionId);
    }

    protected static SimpleServerEntity getPlateAcquisition(UserType userType) {
        return new SimpleServerEntity(
                EntityType.PLATE_ACQUISITION,
                switch (userType) {
                    case UNAUTHENTICATED, ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                    case AUTHENTICATED -> 1;
                }
        );
    }

    protected static String getPlateAcquisitionName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
            case AUTHENTICATED -> "2010-02-23 12:50:30 - 2010-02-23 12:51:29";
        };
    }

    protected static List<Long> getPlateAcquisitionWellIds(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> List.of();
            case AUTHENTICATED -> List.of(13L, 14L, 15L, 16L);
        };
    }

    protected static List<String> getPlateAcquisitionAttributeValues(UserType userType) {
        return List.of(
                getPlateAcquisitionName(userType),
                String.valueOf(getPlateAcquisition(userType).id()),
                getEntityOwner(userType).name(),
                getEntityGroup(userType).name(),
                "-"
        );
    }

    protected static int getPlateAcquisitionMinWellSampleIndex(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
            case AUTHENTICATED -> 0;
        };
    }

    protected static int getPlateAcquisitionMaxWellSampleIndex(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
            case AUTHENTICATED -> 0;
        };
    }

    protected static URI getWellUri(long wellId) {
        return URI.create(getWebServerURI() + "/webclient/?show=well-" + wellId);
    }

    protected static SimpleServerEntity getWell(UserType userType) {
        return new SimpleServerEntity(
                EntityType.WELL,
                switch (userType) {
                    case UNAUTHENTICATED -> 4;
                    case AUTHENTICATED -> 16;
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }

    protected static String getWellName(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> "A2";
            case AUTHENTICATED -> "B2";
            case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
        };
    }

    protected static List<Long> getWellImageIds(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED -> List.of(9L);
            case AUTHENTICATED -> List.of(26L, 36L);
            case ADMIN -> List.of();
        };
    }

    protected static List<String> getWellAttributeValues(UserType userType) {
        return List.of(
                getDatasetName(userType),
                String.valueOf(getDataset(userType).id()),
                getEntityOwner(userType).name(),
                getEntityGroup(userType).name(),
                String.valueOf(getDatasetImageIds(userType).size()),
                switch (userType) {
                    case UNAUTHENTICATED, AUTHENTICATED -> "1";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                },
                switch (userType) {
                    case UNAUTHENTICATED -> "0";
                    case AUTHENTICATED -> "1";
                    case ADMIN -> throw new IllegalArgumentException(String.format("%s not supported", userType));
                }
        );
    }

    protected static List<Annotation> getAnnotationsInDataset(long datasetId) {
        if (datasetId == 1) {
            return new AnnotationGroup(JsonParser.parseString(String.format("""
                {
                    "annotations": [
                        {
                            "owner": {
                                "id": 2
                            },
                            "link": {
                                "owner": {
                                    "id": 2
                                }
                            },
                            "class": "CommentAnnotationI",
                            "textValue": "comment"
                        },
                        {
                            "owner": {
                                "id": 2
                            },
                            "link": {
                                "owner": {
                                    "id": 2
                                }
                            },
                            "class": "FileAnnotationI",
                            "file": {
                                "id": %s,
                                "name": "analysis.csv",
                                "size": 15,
                                "path": "/resources/",
                                "mimetype": "text/csv"
                            }
                        }
                   ],
                   "experimenters": [
                        {
                            "id": 2,
                            "firstName": "public",
                            "lastName": "access"
                        }
                   ]
                }
                """, analysisFileId)).getAsJsonObject()
            );
        } else {
            return new AnnotationGroup(new JsonObject());
        }
    }

    protected static List<SearchResultWithParentInfo> getSearchResultsOnImage(UserType userType) {

    }

    protected static List<SearchResult> getSearchResultsOnDataset(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> List.of(
                    new SearchResult(
                            "dataset",
                            3,
                            "dataset1",
                            null,
                            null,
                            Objects.requireNonNull(getGroupOfEntity(new Dataset(3))).getName(),
                            "/webclient/?show=dataset-3"
                    ),
                    new SearchResult(
                            "dataset",
                            4,
                            "dataset2",
                            null,
                            null,
                            Objects.requireNonNull(getGroupOfEntity(new Dataset(4))).getName(),
                            "/webclient/?show=dataset-4"
                    ),
                    new SearchResult(
                            "dataset",
                            5,
                            "orphaned_dataset1",
                            null,
                            null,
                            Objects.requireNonNull(getGroupOfEntity(new Dataset(5))).getName(),
                            "/webclient/?show=dataset-5"
                    ),
                    new SearchResult(
                            "dataset",
                            6,
                            "orphaned_dataset2",
                            null,
                            null,
                            Objects.requireNonNull(getGroupOfEntity(new Dataset(6))).getName(),
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
                            Objects.requireNonNull(getGroupOfEntity(new Dataset(1))).getName(),
                            "/webclient/?show=dataset-1"
                    ),
                    new SearchResult(
                            "dataset",
                            2,
                            "orphaned_dataset",
                            null,
                            null,
                            Objects.requireNonNull(getGroupOfEntity(new Dataset(2))).getName(),
                            "/webclient/?show=dataset-2"
                    )
            );
            case ADMIN -> List.of();
        };
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

    private static ImageType getImageType(Image image) {
        Map<Function<UserType, Image>, ImageType> imageToType = Map.of(
                OmeroServer::getRgbImage, ImageType.RGB,
                OmeroServer::getUint8Image, ImageType.UINT8,
                OmeroServer::getUint16Image, ImageType.UINT16,
                OmeroServer::getInt16Image, ImageType.INT16,
                OmeroServer::getInt32Image, ImageType.INT32,
                OmeroServer::getFloat32Image, ImageType.FLOAT32,
                OmeroServer::getFloat64Image, ImageType.FLOAT64,
                OmeroServer::getComplexImage, ImageType.COMPLEX
        );

        for (var entry: imageToType.entrySet()) {
            if (Arrays.stream(UserType.values())
                    .map(entry.getKey())
                    .anyMatch(image::equals)) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("Image not recognized");
    }

    private static int getMsPixelBufferApiPort() {
        return omeroWeb == null ? MS_PIXEL_BUFFER_PORT : omeroWeb.getMappedPort(MS_PIXEL_BUFFER_PORT);
    }
}
