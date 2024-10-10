package qupath.ext.omero;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.entities.annotations.AnnotationGroup;
import qupath.ext.omero.core.entities.image.ChannelSettings;
import qupath.ext.omero.core.entities.image.ImageSettings;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.*;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.search.SearchResult;
import qupath.ext.omero.core.pixelapis.mspixelbuffer.MsPixelBufferAPI;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * <p>
 *     An abstract class that gives access to an OMERO server hosted
 *     on a local Docker container. Each subclass of this class will
 *     have access to the same OMERO server.
 * </p>
 * <p>
 *     The OMERO server is populated by several projects, datasets, images,
 *     users, groups. The <a href="https://github.com/glencoesoftware/omero-ms-pixel-buffer">OMERO
 *     Pixel Data Microservice</a> is also installed on the server.
 * </p>
 * <p>
 *     All useful information of the OMERO server are returned by functions
 *     of this class.
 * </p>
 * <p>
 *     If Docker can't be found on the host machine, all tests are skipped.
 * </p>
 * <p>
 *     If a Docker container containing a working OMERO server is already
 *     running on the host machine, set the {@link #IS_LOCAL_OMERO_SERVER_RUNNING}
 *     variable to {@code true}. This will prevent this class to create new containers,
 *     gaining some time when running the tests.
 *     A local OMERO server can be started by running the qupath-extension-omero/src/test/resources/server.sh
 *     script.
 * </p>
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
    protected enum UserType {
        USER,
        PUBLIC
    }
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

    static {
        if (!dockerAvailable || IS_LOCAL_OMERO_SERVER_RUNNING) {
            postgres = null;
            redis = null;
            omeroServer = null;
            omeroWeb = null;
            analysisFileId = "85";
        } else {
            // See https://hub.docker.com/r/openmicroscopy/omero-server
            postgres = new GenericContainer<>(DockerImageName.parse("postgres"))
                    .withNetwork(Network.SHARED)
                    .withNetworkAliases("postgres")
                    .withEnv("POSTGRES_PASSWORD", "postgres")
                    .withLogConsumer(frame ->
                            logger.debug(String.format("Postgres container: %s", frame.getUtf8String()))
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
                            while (true) {
                                logger.info("Attempting to connect to the OMERO server");

                                try {
                                    RequestSender.isLinkReachableWithGet(URI.create(getWebServerURI())).get();
                                    logger.info("Connection to the OMERO server succeeded");
                                    return;
                                } catch (Exception ignored) {
                                    logger.info("Connection to the OMERO server failed. Retrying in five seconds.");
                                }

                                try {
                                    TimeUnit.SECONDS.sleep(5);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
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
                            logger.debug(String.format("OMERO server container: %s", frame.getUtf8String()))
                    );

            // See https://github.com/glencoesoftware/omero-ms-pixel-buffer:
            // OMERO.web needs to use Redis backed sessions
            redis = new GenericContainer<>(DockerImageName.parse("redis"))
                    .withNetwork(Network.SHARED)
                    .withNetworkAliases("redis")
                    .withLogConsumer(frame ->
                            logger.debug(String.format("Redis container: %s", frame.getUtf8String()))
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
                            logger.debug(String.format("OMERO web container: %s", frame.getUtf8String()))
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
                        logger.debug(String.format("Connection to OMERO failed: %s\nWaiting 1 second and retrying...", rootConnectionResult.getStderr()));
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
                Container.ExecResult omeroWebInstallPixelMsResult = omeroWeb.execInContainerWithUser(
                        "root",
                        "/resources/installPixelBufferMs.sh"
                );
                logCommandResult(omeroWebInstallPixelMsResult);

                // Set up the OMERO web container (by starting the pixel buffer microservice)
                Container.ExecResult omeroWebRunPixelMsResult = omeroWeb.execInContainerWithUser(
                        "root",
                        "/resources/runPixelBufferMs.sh"
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

    protected static String getServerURI() {
        return "omero-server";
    }

    protected static int getServerPort() {
        return OMERO_SERVER_PORT;
    }

    protected static WebClient createClient(UserType userType) throws ExecutionException, InterruptedException {
        return switch (userType) {
            case USER -> createValidClient(
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    getUsername(userType),
                    "-p",
                    getPassword(userType)
            );
            case PUBLIC -> createValidClient(WebClient.Authentication.SKIP);
        };
    }

    protected static String getUsername(UserType userType) {
        return getConnectedOwner(userType).username();
    }

    protected static String getPassword(UserType userType) {
        return switch (userType) {
            case USER -> "password_user";
            case PUBLIC -> "password_public";
        };
    }

    protected static List<Group> getGroups(UserType userType) {
        return switch (userType) {
            case USER -> List.of(
                    getGroup1(),
                    getGroup2(),
                    getGroup3()
            );
            case PUBLIC -> List.of(
                    getPublicGroup()
            );
        };
    }

    protected static Group getDefaultGroup(UserType userType) {
        return switch (userType) {
            case USER -> getGroup1();
            case PUBLIC -> getPublicGroup();
        };
    }

    protected static List<Owner> getOwners(UserType userType) {
        return switch (userType) {
            case USER -> List.of(
                    getUser1(),
                    getUser2(),
                    getUser()
            );
            case PUBLIC -> List.of(
                    getPublicUser()
            );
        };
    }

    protected static Owner getConnectedOwner(UserType userType) {
        return switch (userType) {
            case USER -> getUser();
            case PUBLIC -> getPublicUser();
        };
    }

    protected static List<Project> getProjects(UserType userType) {
        return switch (userType) {
            case USER -> List.of(new Project(2));
            case PUBLIC -> List.of(new Project(1));
        };
    }

    protected static URI getProjectURI(Project project) {
        return URI.create(getWebServerURI() + "/webclient/?show=project-" + project.getId());
    }

    protected static List<String> getProjectAttributeValue(Project project) {
        return List.of(
                "project",
                String.valueOf(project.getId()),
                "-",
                getOwnerOfEntity(project).getFullName(),
                getGroupOfEntity(project).getName(),
                switch ((int) project.getId()) {
                    case 1 -> "1";
                    case 2 -> "2";
                    default -> "0";
                }
        );
    }

    protected static List<Dataset> getDatasets(UserType userType) {
        return switch (userType) {
            case USER -> List.of(new Dataset(3), new Dataset(4));
            case PUBLIC -> List.of(new Dataset(1));
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
                getOwnerOfEntity(dataset).getFullName(),
                getGroupOfEntity(dataset).getName(),
                switch ((int) dataset.getId()) {
                    case 1 -> "7";
                    case 4 -> "2";
                    default -> "0";
                }
        );
    }

    protected static List<Dataset> getOrphanedDatasets(UserType userType) {
        return switch (userType) {
            case USER -> List.of(new Dataset(5), new Dataset(6));
            case PUBLIC -> List.of(new Dataset(2));
        };
    }

    protected static URI getDatasetURI(Dataset dataset) {
        return URI.create(getWebServerURI() + "/webclient/?show=dataset-" + dataset.getId());
    }

    protected static Image getRGBImage(UserType userType) {
        return switch (userType) {
            case USER -> new Image(19);
            case PUBLIC -> new Image(1);
        };
    }

    protected static Image getUint8Image(UserType userType) {
        return switch (userType) {
            case USER -> new Image(41);
            case PUBLIC -> new Image(2);
        };
    }

    protected static Image getUint16Image(UserType userType) {
        return switch (userType) {
            case USER -> new Image(42);
            case PUBLIC -> new Image(3);
        };
    }

    protected static Image getInt16Image(UserType userType) {
        return switch (userType) {
            case USER -> new Image(43);
            case PUBLIC -> new Image(4);
        };
    }

    protected static Image getInt32Image(UserType userType) {
        return switch (userType) {
            case USER -> new Image(44);
            case PUBLIC -> new Image(5);
        };
    }

    protected static Image getFloat32Image(UserType userType) {
        return switch (userType) {
            case USER -> new Image(20);
            case PUBLIC -> new Image(6);
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
            case USER -> new Image(45);
            case PUBLIC -> new Image(7);
        };
    }

    protected static Image getComplexImage(UserType userType) {
        return switch (userType) {
            case USER -> new Image(46);
            case PUBLIC -> new Image(8);
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
                getOwnerOfEntity(image).getFullName(),
                getGroupOfEntity(image).getName(),
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
            case USER -> List.of(
                    getComplexImage(userType),
                    getFloat64Image(userType),
                    getInt16Image(userType),
                    getInt32Image(userType),
                    getUint16Image(userType),
                    getUint8Image(userType)
            );
            case PUBLIC -> List.of(getComplexImage(userType));
        };
    }

    protected static List<Image> getImagesInDataset(Dataset dataset) {
        return switch ((int) dataset.getId()) {
            case 1 -> List.of(
                    getFloat32Image(UserType.PUBLIC),
                    getFloat64Image(UserType.PUBLIC),
                    getInt16Image(UserType.PUBLIC),
                    getInt32Image(UserType.PUBLIC),
                    getRGBImage(UserType.PUBLIC),
                    getUint16Image(UserType.PUBLIC),
                    getUint8Image(UserType.PUBLIC)
            );
            case 4 -> List.of(
                    getFloat32Image(UserType.USER),
                    getRGBImage(UserType.USER)
            );
            default -> List.of();
        };
    }

    protected static List<Image> getImagesInPlate(Plate plate) {
        if (plate.getId() < 3) {
            return List.of(
                    new Image(9 + (plate.getId()-1) * 5),
                    new Image(10 + (plate.getId()-1) * 5),
                    new Image(11 + (plate.getId()-1) * 5),
                    new Image(12 + (plate.getId()-1) * 5),
                    new Image(13 + (plate.getId()-1) * 5)
            );
        } else if (plate.getId() < 7) {
            return List.of(
                    new Image(11 + (plate.getId()-1) * 5),
                    new Image(12 + (plate.getId()-1) * 5),
                    new Image(13 + (plate.getId()-1) * 5),
                    new Image(14 + (plate.getId()-1) * 5),
                    new Image(15 + (plate.getId()-1) * 5)
            );
        } {
            return List.of();
        }
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

    protected static URI getImageURI(Image image) {
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
            case USER -> List.of(new Screen(2), new Screen(3));
            case PUBLIC -> List.of(new Screen(1));
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
                getOwnerOfEntity(screen).getFullName(),
                getGroupOfEntity(screen).getName(),
                "1"
        );
    }

    protected static List<Plate> getOrphanedPlates(UserType userType) {
        return switch (userType) {
            case USER -> List.of(new Plate(5), new Plate(6));
            case PUBLIC -> List.of(new Plate(2));
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

    protected static List<PlateAcquisition> getPlateAcquisitionsInScreen() {
        return List.of();
    }

    protected static List<String> getPlateAttributeValue(Plate plate) {
        return List.of(
                "plate",
                String.valueOf(plate.getId()),
                getOwnerOfEntity(plate).getFullName(),
                getGroupOfEntity(plate).getName(),
                "3",
                "3"
        );
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
            case USER -> List.of(
                    new SearchResult(
                            "dataset",
                            3,
                            "dataset1",
                            getGroupOfEntity(new Dataset(3)).getName(),
                            "/webclient/?show=dataset-3",
                            null,
                            null
                    ),
                    new SearchResult(
                            "dataset",
                            4,
                            "dataset2",
                            getGroupOfEntity(new Dataset(4)).getName(),
                            "/webclient/?show=dataset-4",
                            null,
                            null
                    ),
                    new SearchResult(
                            "dataset",
                            5,
                            "orphaned_dataset1",
                            getGroupOfEntity(new Dataset(5)).getName(),
                            "/webclient/?show=dataset-5",
                            null,
                            null
                    ),
                    new SearchResult(
                            "dataset",
                            6,
                            "orphaned_dataset2",
                            getGroupOfEntity(new Dataset(6)).getName(),
                            "/webclient/?show=dataset-6",
                            null,
                            null
                    )
            );
            case PUBLIC -> List.of(
                    new SearchResult(
                            "dataset",
                            1,
                            "dataset",
                            getGroupOfEntity(new Dataset(1)).getName(),
                            "/webclient/?show=dataset-1",
                            null,
                            null
                    ),
                    new SearchResult(
                            "dataset",
                            2,
                            "orphaned_dataset",
                            getGroupOfEntity(new Dataset(2)).getName(),
                            "/webclient/?show=dataset-2",
                            null,
                            null
                    )
            );
        };
    }

    private static void logCommandResult(Container.ExecResult result) {
        if (!result.getStdout().isBlank()) {
            logger.debug(String.format("Setting up OMERO server: %s", result.getStdout()));
        }

        if (!result.getStderr().isBlank()) {
            logger.warn(String.format("Setting up OMERO server: %s", result.getStderr()));
        }
    }

    private static WebClient createValidClient(WebClient.Authentication authentication, String... args) {
        WebClient webClient = null;
        int attempt = 0;

        do {
            try {
                webClient = WebClients.createClient(getWebServerURI(), authentication, args).get();
            } catch (ExecutionException | InterruptedException e) {
                logger.debug(String.format("Client creation attempt %d of %d failed", attempt, CLIENT_CREATION_ATTEMPTS-1), e);
            }
        } while (webClient != null && ++attempt < CLIENT_CREATION_ATTEMPTS);

        if (webClient == null) {
            throw new IllegalStateException("Client creation failed");
        } else {
            webClient.getPixelAPI(MsPixelBufferAPI.class).setPort(getMsPixelBufferApiPort(), true);
            return webClient;
        }
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

    private static Group getPublicGroup() {
        return new Group(3, "public-data");
    }

    private static Owner getUser1() {
        return new Owner(3, "user1", "", "user1", "", "", "user1");
    }

    private static Owner getUser2() {
        return new Owner(4, "user2", "", "user2", "", "", "user2");
    }

    private static Owner getUser() {
        return new Owner(6, "user", "", "user", "", "", "user");
    }

    private static Owner getPublicUser() {
        return new Owner(2, "public", "", "access", "", "", "public");
    }

    private static Owner getOwnerOfEntity(ServerEntity serverEntity) {
        return switch (serverEntity) {
            case Image image -> switch ((int) image.getId()) {
                case 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18 -> getPublicUser();
                case 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40 -> getUser1();
                case 41, 42, 43, 44, 45 -> getUser2();
                case 46 -> getUser();
                default -> null;
            };
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
            case null, default -> null;
        };
    }

    private static Group getGroupOfEntity(ServerEntity serverEntity) {
        return switch (serverEntity) {
            case Image image -> switch ((int) image.getId()) {
                case 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18 -> getPublicGroup();
                case 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 46 -> getGroup1();
                case 41, 42, 43, 44, 45 -> getGroup2();
                default -> null;
            };
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
            case null, default -> null;
        };
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
                    .anyMatch(i -> i.equals(image))) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("Image not recognized");
    }

    private static int getMsPixelBufferApiPort() {
        return omeroWeb == null ? MS_PIXEL_BUFFER_PORT : omeroWeb.getMappedPort(MS_PIXEL_BUFFER_PORT);
    }
}
