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
import qupath.ext.omero.core.entities.annotations.AnnotationGroup;
import qupath.ext.omero.core.entities.image.ChannelSettings;
import qupath.ext.omero.core.entities.image.ImageSettings;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.*;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.search.SearchResult;
import qupath.ext.omero.core.pixelapis.mspixelbuffer.MsPixelBufferApi;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;

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

    protected static String getServerURI() {
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
        return getConnectedOwner(userType).username();
    }

    protected static String getPassword(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> "password_user";
            case UNAUTHENTICATED -> "password_public";
            case ADMIN -> "password_admin";
        };
    }

    protected static List<Group> getGroups(UserType userType) {
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

    protected static List<Group> getGroups() {
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

    protected static Group getDefaultGroup(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> getGroup1();
            case UNAUTHENTICATED -> getPublicGroup();
            case ADMIN -> getSystemGroup();
        };
    }

    protected static Group getGroupOfEntity(ServerEntity serverEntity) {
        return switch (serverEntity) {
            case Image image -> {
                if (image.getId() < 19) {
                    yield getPublicGroup();
                } else if (image.getId() < 53 || image.getId() == 58) {
                    yield getGroup1();
                } else if (image.getId() < 58) {
                    yield getGroup2();
                } else {
                    yield null;
                }
            }
            case Project project -> switch ((int) project.getId()) {
                case 1 -> getPublicGroup();
                case 2 -> getGroup1();
                default -> null;
            };
            case Dataset dataset -> switch ((int) dataset.getId()) {
                case 1, 2 -> getPublicGroup();
                case 3, 4, 5, 6 -> getGroup1();
                default -> null;
            };
            case Screen screen -> switch ((int) screen.getId()) {
                case 1 -> getPublicGroup();
                case 2, 3 -> getGroup1();
                default -> null;
            };
            case Plate plate -> switch ((int) plate.getId()) {
                case 1, 2 -> getPublicGroup();
                case 3, 4, 5, 6 -> getGroup1();
                default -> null;
            };
            case PlateAcquisition plateAcquisition -> switch ((int) plateAcquisition.getId()) {
                case 1, 2 -> getGroup1();
                default -> null;
            };
            case Well well -> {
                if (well.getId() < 9) {
                    yield getPublicGroup();
                } else if (well.getId() < 25) {
                    yield getGroup1();
                } else {
                    yield null;
                }
            }
            case null, default -> null;
        };
    }

    protected static List<Group> getGroupsOwnedByUser(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> List.of();
            case AUTHENTICATED -> List.of(getGroup3());
        };
    }

    protected static List<Owner> getOwners(UserType userType) {
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

    protected static Owner getConnectedOwner(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> getUser();
            case UNAUTHENTICATED -> getPublicUser();
            case ADMIN -> getAdminUser();
        };
    }

    protected static Owner getOwnerOfEntity(ServerEntity serverEntity) {
        return switch (serverEntity) {
            case Image image -> {
                if (image.getId() < 19) {
                    yield getPublicUser();
                } else if (image.getId() < 53) {
                    yield getUser1();
                } else if (image.getId() < 58) {
                    yield getUser2();
                } else if (image.getId() < 59) {
                    yield getUser();
                } else {
                    yield null;
                }
            }
            case Project project -> switch ((int) project.getId()) {
                case 1 -> getPublicUser();
                case 2 -> getUser1();
                default -> null;
            };
            case Dataset dataset -> switch ((int) dataset.getId()) {
                case 1, 2 -> getPublicUser();
                case 3, 4, 5, 6 -> getUser1();
                default -> null;
            };
            case Screen screen -> switch ((int) screen.getId()) {
                case 1 -> getPublicUser();
                case 2, 3 -> getUser1();
                default -> null;
            };
            case Plate plate -> switch ((int) plate.getId()) {
                case 1, 2 -> getPublicUser();
                case 3, 4, 5, 6 -> getUser1();
                default -> null;
            };
            case PlateAcquisition plateAcquisition -> switch ((int) plateAcquisition.getId()) {
                case 1, 2 -> getUser1();
                default -> null;
            };
            case Well well -> {
                if (well.getId() < 9) {
                    yield getPublicUser();
                } else if (well.getId() < 25) {
                    yield getUser1();
                } else {
                    yield null;
                }
            }
            case null, default -> null;
        };
    }

    protected static List<Project> getProjects(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> List.of(new Project(2));
            case UNAUTHENTICATED -> List.of(new Project(1));
            case ADMIN -> List.of(new Project(1), new Project(2));
        };
    }

    protected static URI getProjectUri(Project project) {
        return URI.create(getWebServerURI() + "/webclient/?show=project-" + project.getId());
    }

    protected static List<String> getProjectAttributeValue(Project project) {
        return List.of(
                "project",
                String.valueOf(project.getId()),
                "-",
                Objects.requireNonNull(getOwnerOfEntity(project)).getFullName(),
                Objects.requireNonNull(getGroupOfEntity(project)).getName(),
                switch ((int) project.getId()) {
                    case 1 -> "1";
                    case 2 -> "2";
                    default -> "0";
                }
        );
    }

    protected static List<Dataset> getDatasets(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> List.of(new Dataset(3), new Dataset(4));
            case UNAUTHENTICATED -> List.of(new Dataset(1));
            case ADMIN -> List.of();
        };
    }

    protected static List<Dataset> getDatasetsInProject(Project project) {
        return switch ((int) project.getId()) {
            case 1 -> List.of(new Dataset(1));
            case 2 -> List.of(new Dataset(3), new Dataset(4));
            default -> List.of();
        };
    }

    protected static List<String> getDatasetAttributeValue(Dataset dataset) {
        return List.of(
                switch ((int) dataset.getId()) {
                    case 1 -> "dataset";
                    case 2 -> "orphaned_dataset";
                    case 3 -> "dataset1";
                    case 4 -> "dataset2";
                    case 5 -> "orphaned_dataset1";
                    case 6 -> "orphaned_dataset2";
                    default -> "";
                },
                String.valueOf(dataset.getId()),
                "-",
                Objects.requireNonNull(getOwnerOfEntity(dataset)).getFullName(),
                Objects.requireNonNull(getGroupOfEntity(dataset)).getName(),
                switch ((int) dataset.getId()) {
                    case 1 -> "7";
                    case 4 -> "2";
                    default -> "0";
                }
        );
    }

    protected static List<Dataset> getOrphanedDatasets(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> List.of(new Dataset(5), new Dataset(6));
            case UNAUTHENTICATED -> List.of(new Dataset(2));
            case ADMIN -> List.of(new Dataset(2), new Dataset(5), new Dataset(6));
        };
    }

    protected static URI getDatasetUri(Dataset dataset) {
        return URI.create(getWebServerURI() + "/webclient/?show=dataset-" + dataset.getId());
    }

    protected static Image getRGBImage(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> new Image(19);
            case UNAUTHENTICATED -> new Image(1);
            case ADMIN -> null;
        };
    }

    protected static Image getUint8Image(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> new Image(53);
            case UNAUTHENTICATED -> new Image(2);
            case ADMIN -> null;
        };
    }

    protected static Image getUint16Image(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> new Image(54);
            case UNAUTHENTICATED -> new Image(3);
            case ADMIN -> null;
        };
    }

    protected static Image getInt16Image(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> new Image(55);
            case UNAUTHENTICATED -> new Image(4);
            case ADMIN -> null;
        };
    }

    protected static Image getInt32Image(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> new Image(56);
            case UNAUTHENTICATED -> new Image(5);
            case ADMIN -> null;
        };
    }

    protected static Image getFloat32Image(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> new Image(20);
            case UNAUTHENTICATED -> new Image(6);
            case ADMIN -> null;
        };
    }

    protected static List<ChannelSettings> getFloat32ChannelSettings() {
        return List.of(
                new ChannelSettings("0", 0, 211, Integer.parseInt("FF0000", 16)),
                new ChannelSettings("1", 0, 248, Integer.parseInt("00FF00", 16)),
                new ChannelSettings("2", 0, 184, Integer.parseInt("0000FF", 16))
        );
    }

    protected static ImageSettings getFloat32ImageSettings() {
        return new ImageSettings("float32.tiff", getFloat32ChannelSettings());
    }

    protected static Image getFloat64Image(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> new Image(57);
            case UNAUTHENTICATED -> new Image(7);
            case ADMIN -> null;
        };
    }

    protected static Image getComplexImage(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> new Image(58);
            case UNAUTHENTICATED -> new Image(8);
            case ADMIN -> null;
        };
    }

    protected static ImageServerMetadata getImageMetadata(Image image) {
        return switch (getImageType(image)) {
            case RGB -> new ImageServerMetadata.Builder()
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
            case COMPLEX -> new ImageServerMetadata.Builder()
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
            default -> new ImageServerMetadata.Builder().build();
        };
    }

    protected static List<String> getImageAttributeValue(Image image) {
        ImageServerMetadata metadata = getImageMetadata(image);

        return List.of(
                metadata.getName(),
                String.valueOf(image.getId()),
                Objects.requireNonNull(getOwnerOfEntity(image)).getFullName(),
                Objects.requireNonNull(getGroupOfEntity(image)).getName(),
                "-",
                metadata.getWidth() + " px",
                metadata.getHeight() + " px",
                switch (getImageType(image)) {
                    case RGB -> "0.2 MB";
                    case COMPLEX -> "7.5 MB";
                    default -> "";
                },
                String.valueOf(metadata.getSizeZ()),
                String.valueOf(metadata.getSizeC()),
                String.valueOf(metadata.getSizeT()),
                metadata.getPixelWidthMicrons() + " µm",
                metadata.getPixelHeightMicrons() + " µm",
                Double.isNaN(metadata.getZSpacingMicrons()) ? "-" : metadata.getZSpacingMicrons() + " µm",
                switch (metadata.getPixelType()) {
                    case UINT8 -> "uint8";
                    case INT8 -> "int8";
                    case UINT16 -> "uint16";
                    case INT16 -> "int16";
                    case UINT32 -> "uint32";
                    case INT32 -> "int32";
                    case FLOAT32 -> "float";
                    case FLOAT64 -> "double";
                }
        );
    }

    protected static List<Image> getOrphanedImages(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> List.of(
                    getComplexImage(userType),
                    getFloat64Image(userType),
                    getInt16Image(userType),
                    getInt32Image(userType),
                    getUint16Image(userType),
                    getUint8Image(userType)
            );
            case UNAUTHENTICATED -> List.of(getComplexImage(userType));
            case ADMIN -> List.of();
        };
    }

    protected static List<Image> getImagesInDataset(Dataset dataset) {
        return switch ((int) dataset.getId()) {
            case 1 -> List.of(
                    getFloat32Image(UserType.UNAUTHENTICATED),
                    getFloat64Image(UserType.UNAUTHENTICATED),
                    getInt16Image(UserType.UNAUTHENTICATED),
                    getInt32Image(UserType.UNAUTHENTICATED),
                    getRGBImage(UserType.UNAUTHENTICATED),
                    getUint16Image(UserType.UNAUTHENTICATED),
                    getUint8Image(UserType.UNAUTHENTICATED)
            );
            case 4 -> List.of(
                    getFloat32Image(UserType.AUTHENTICATED),
                    getRGBImage(UserType.AUTHENTICATED)
            );
            default -> List.of();
        };
    }

    protected static Image getAnnotableImage(UserType userType) {
        return getRGBImage(userType);
    }

    protected static Image getModifiableImage(UserType userType) {
        return getComplexImage(userType);
    }

    protected static List<ChannelSettings> getModifiableImageChannelSettings() {
        return List.of(
                new ChannelSettings("0", 0, 240, Integer.parseInt("808080", 16))
        );
    }

    protected static URI getImageUri(Image image) {
        return URI.create(getWebServerURI() + "/webclient/?show=image-" + image.getId());
    }

    protected static double getImageRedChannelMean(Image image) {
        return switch (getImageType(image)) {
            case RGB -> 5.414;
            case UINT8 -> 7.134;
            case UINT16 -> 4.295;
            case INT16 -> 6.730;
            case INT32 -> 4.574;
            case FLOAT32 -> 8.429;
            case FLOAT64 -> 7.071;
            case COMPLEX -> 0.0;
        };
    }

    protected static double getImageRedChannelStdDev(Image image) {
        return switch (getImageType(image)) {
            case RGB -> 18.447;
            case UINT8 -> 24.279;
            case UINT16 -> 14.650;
            case INT16 -> 22.913;
            case INT32 -> 15.597;
            case FLOAT32 -> 28.605;
            case FLOAT64 -> 23.995;
            case COMPLEX -> 0.0;
        };
    }

    protected static List<Screen> getScreens(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> List.of(new Screen(2), new Screen(3));
            case UNAUTHENTICATED -> List.of(new Screen(1));
            case ADMIN -> List.of(new Screen(1), new Screen(2), new Screen(3));
        };
    }

    protected static List<String> getScreenAttributeValue(Screen screen) {
        return List.of(
                switch ((int) screen.getId()) {
                    case 1 -> "screen";
                    case 2 -> "screen1";
                    case 3 -> "screen2";
                    default -> "";
                },
                String.valueOf(screen.getId()),
                "-",
                Objects.requireNonNull(getOwnerOfEntity(screen)).getFullName(),
                Objects.requireNonNull(getGroupOfEntity(screen)).getName(),
                "1"
        );
    }

    protected static URI getScreenUri(Screen screen) {
        return URI.create(getWebServerURI() + "/webclient/?show=screen-" + screen.getId());
    }

    protected static List<Plate> getPlates(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> List.of(new Plate(3), new Plate(4), new Plate(5), new Plate(6));
            case UNAUTHENTICATED -> List.of(new Plate(1), new Plate(2));
            case ADMIN -> List.of(new Plate(1), new Plate(2), new Plate(3), new Plate(4), new Plate(5), new Plate(6));
        };
    }

    protected static List<Plate> getOrphanedPlates(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> List.of(new Plate(5), new Plate(6));
            case UNAUTHENTICATED -> List.of(new Plate(2));
            case ADMIN -> List.of(new Plate(2), new Plate(5), new Plate(6));
        };
    }

    protected static List<Plate> getPlatesInScreen(Screen screen) {
        return switch ((int) screen.getId()) {
            case 1 -> List.of(new Plate(1));
            case 2 -> List.of(new Plate(3));
            case 3 -> List.of(new Plate(4));
            default -> List.of();
        };
    }
    
    protected static List<Plate> getPlatesOwningPlateAcquisition(UserType userType) {
        return switch (userType) {
            case UNAUTHENTICATED, ADMIN -> List.of();
            case AUTHENTICATED -> List.of(new Plate(4), new Plate(6));
        };
    }

    protected static List<String> getPlateAttributeValue(Plate plate) {
        return List.of(
                switch ((int) plate.getId()) {
                    case 1, 2, 3, 5 -> "plate";
                    case 4, 6 -> "plate-plate_acquisition-well.xml";
                    default -> "-";
                },
                String.valueOf(plate.getId()),
                Objects.requireNonNull(getOwnerOfEntity(plate)).getFullName(),
                Objects.requireNonNull(getGroupOfEntity(plate)).getName(),
                "3",
                "3"
        );
    }

    protected static URI getPlateUri(Plate plate) {
        return URI.create(getWebServerURI() + "/webclient/?show=plate-" + plate.getId());
    }

    protected static List<PlateAcquisition> getPlateAcquisitions(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED, ADMIN -> List.of(new PlateAcquisition(1), new PlateAcquisition(2), new PlateAcquisition(3), new PlateAcquisition(4));
            case UNAUTHENTICATED -> List.of();
        };
    }

    protected static List<PlateAcquisition> getPlateAcquisitionsInPlate(Plate plate) {
        return switch ((int) plate.getId()) {
            case 4 -> List.of(new PlateAcquisition(1), new PlateAcquisition(2));
            case 6 -> List.of(new PlateAcquisition(3), new PlateAcquisition(4));
            default -> List.of();
        };
    }

    protected static List<String> getPlateAcquisitionAttributeValue(PlateAcquisition plateAcquisition) {
        return List.of(
                "-",
                String.valueOf(plateAcquisition.getId()),
                Objects.requireNonNull(getOwnerOfEntity(plateAcquisition)).getFullName(),
                Objects.requireNonNull(getGroupOfEntity(plateAcquisition)).getName(),
                switch ((int) plateAcquisition.getId()) {
                    case 1, 3 -> "2010-02-23 12:50:30";
                    case 2, 4 -> "2010-02-23 12:49:30";
                    default -> "-";
                }
        );
    }

    protected static URI getPlateAcquisitionUri(PlateAcquisition plateAcquisition) {
        return URI.create(getWebServerURI() + "/webclient/?show=run-" + plateAcquisition.getId());
    }

    protected static List<Well> getWells(UserType userType) {
        return switch (userType) {
            case AUTHENTICATED -> IntStream.range(9, 25).mapToObj(Well::new).toList();
            case UNAUTHENTICATED -> IntStream.range(1, 9).mapToObj(Well::new).toList();
            case ADMIN -> IntStream.range(1, 25).mapToObj(Well::new).toList();
        };
    }

    protected static List<Well> getWellsInPlate(Plate plate) {
        if (plate.getId() < 7) {
            return List.of(
                    new Well(1 + (plate.getId()-1) * 4),
                    new Well(2 + (plate.getId()-1) * 4),
                    new Well(3 + (plate.getId()-1) * 4),
                    new Well(4 + (plate.getId()-1) * 4)
            );
        } else {
            return List.of();
        }
    }

    protected static List<Well> getWellsInPlateAcquisition(PlateAcquisition plateAcquisition) {
        if (plateAcquisition.getId() == 1 || plateAcquisition.getId() == 2) {
            return List.of(
                    new Well(13),
                    new Well(14),
                    new Well(15),
                    new Well(16)
            );
        } else {
            return List.of();
        }
    }

    protected static URI getWellUri(Well well) {
        return URI.create(getWebServerURI() + "/webclient/?show=well-" + well.getId());
    }

    protected static AnnotationGroup getAnnotationsInDataset(Dataset dataset) {
        if (dataset.getId() == 1) {
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

    private static Group getSystemGroup() {
        return new Group(0, "system");
    }

    private static Group getUserGroup() {
        return new Group(1, "user");
    }

    private static Group getGuestGroup() {
        return new Group(2, "guest");
    }

    private static Group getPublicGroup() {
        return new Group(3, "public-data");
    }

    private static Group getGroup1() {
        return new Group(4, "group1");
    }

    private static Group getGroup2() {
        return new Group(5, "group2");
    }

    private static Group getGroup3() {
        return new Group(6, "group3");
    }

    private static Owner getRootUser() {
        return new Owner(0, "root", "", "root", "", "", "root");
    }

    private static Owner getGuestUser() {
        return new Owner(1, "Guest", "", "Account", "", "", "guest");
    }

    private static Owner getAdminUser() {
        return new Owner(2, "admin", "", "admin", "", "", "admin");
    }

    private static Owner getPublicUser() {
        return new Owner(3, "public", "", "access", "", "", "public");
    }

    private static Owner getUser1() {
        return new Owner(4, "user1", "", "user1", "", "", "user1");
    }

    private static Owner getUser2() {
        return new Owner(5, "user2", "", "user2", "", "", "user2");
    }

    private static Owner getUser3() {
        return new Owner(6, "user3", "", "user3", "", "", "user3");
    }

    private static Owner getUser() {
        return new Owner(7, "user", "", "user", "", "", "user");
    }

    private static ImageType getImageType(Image image) {
        Map<Function<UserType, Image>, ImageType> imageToType = Map.of(
                OmeroServer::getRGBImage, ImageType.RGB,
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
