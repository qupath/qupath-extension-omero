package qupath.ext.omero;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
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
import qupath.lib.images.servers.PixelType;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
    private static final boolean IS_LOCAL_OMERO_SERVER_RUNNING = true;
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
                                    WebClient client = createRootClient();
                                    WebClients.removeClient(client);
                                    logger.info("Connection to the OMERO server succeeded");
                                    return;
                                } catch (IllegalStateException | ExecutionException | InterruptedException ignored) {
                                    logger.info("Connection to the OMERO server failed. Retrying in one second.");
                                }

                                try {
                                    TimeUnit.SECONDS.sleep(1);
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
                    .withEnv("CONFIG_omero_web_public_password", "password")
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

    @AfterAll
    static void closeContainer() {
        if (omeroWeb != null) {
            omeroWeb.close();
        }
        if (omeroServer != null) {
            omeroServer.close();
        }
        if (postgres != null) {
            postgres.close();
        }
        if (redis != null) {
            redis.close();
        }
    }

    protected static String getWebServerURI() {
        return omeroWeb == null ?
                "http://localhost:" + OMERO_WEB_PORT :
                "http://" + omeroWeb.getHost() + ":" + omeroWeb.getMappedPort(OMERO_WEB_PORT);
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
        return switch (userType) {
            case USER -> "user";
            case PUBLIC -> "public";
        };
    }

    protected static String getPassword(UserType userType) {
        return switch (userType) {
            case USER -> "password_user";
            case PUBLIC -> "password_public";
        };
    }

    protected static long getUserId(UserType userType) {
        return switch (userType) {
            case USER -> 5;
            case PUBLIC -> 3;
        };
    }

    protected static List<Group> getGroups(UserType userType) {
        return switch (userType) {
            case USER -> List.of(new Group(4, "group1"), new Group(5, "group2"));
            case PUBLIC -> List.of(new Group(3, "public-data"));
        };
    }

    protected static List<Dataset> getDatasets(UserType userType) {
        return switch (userType) {
            case USER -> List.of(new Dataset(2), new Dataset(3));
            case PUBLIC -> List.of(new Dataset(1));
        };
    }

    protected static List<Project> getProjects(UserType userType) {
        return switch (userType) {
            case USER -> List.of(new Project(1));
            case PUBLIC -> List.of(new Project(2));
        };
    }

    protected static List<Dataset> getDatasetsInProject(Project project) {
        return switch ((int) project.getId()) {
            case 1 -> List.of(new Dataset(2), new Dataset(3));
            case 2 -> List.of(new Dataset(1));
            default -> List.of();
        };
    }

    protected static URI getProjectURI(Project project) {
        return URI.create(getWebServerURI() + "/webclient/?show=project-" + project.getId());
    }

    protected static URI getDatasetURI(Dataset dataset) {
        return URI.create(getWebServerURI() + "/webclient/?show=dataset-" + dataset.getId());
    }

    protected static List<URI> getImagesUriInDataset(Dataset dataset) {
        return getImagesInDataset(dataset).stream()
                .map(image -> URI.create(getWebServerURI() + "/webclient/?show=image-" + image.getId()))
                .toList();
    }

    protected static List<Image> getImagesInDataset(Dataset dataset) {
        if (dataset.getId() == 1) {
            return List.of(
                    new Image(1),
                    new Image(2),
                    new Image(3),
                    new Image(4),
                    new Image(5),
                    new Image(6),
                    new Image(7),
                    new Image(8),
                    new Image(9)
            );
        } else if (dataset.getId() == 2) {
            return List.of();
        } else if (dataset.getId() == 3) {
            return List.of(new Image(10));
        } else {
            return List.of();
        }
    }

    protected static Image getRGBImage(UserType userType) {
        return switch (userType) {
            case USER -> new Image(1);
            case PUBLIC -> new Image(10);
        };
    }

    protected static List<Image> getOrphanedImage(UserType userType) {
        return switch (userType) {
            case USER -> List.of(new Image(12));
            case PUBLIC -> List.of(new Image(10));
        };
    }

    protected static URI getRGBImageURI(UserType userType) {
        return URI.create(getWebServerURI() + "/webclient/?show=image-" + getRGBImage(userType).getId());
    }



















    protected static WebClient createUnauthenticatedClient() throws ExecutionException, InterruptedException {
        return createValidClient(WebClient.Authentication.SKIP);
    }

    protected static WebClient createAuthenticatedClient() throws ExecutionException, InterruptedException {
        return createValidClient(
                WebClient.Authentication.ENFORCE,
                "-u",
                getUserUsername(),
                "-p",
                getUserPassword()
        );
    }

    protected static WebClient createRootClient() throws ExecutionException, InterruptedException {
        return createValidClient(
                WebClient.Authentication.ENFORCE,
                "-u",
                getRootUsername(),
                "-p",
                getRootPassword()
        );
    }

    protected static String getServerURI() {
        return "omero-server";
    }

    protected static int getServerPort() {
        return OMERO_SERVER_PORT;
    }

    protected static String getRootUsername() {
        return "root";
    }

    protected static String getRootPassword() {
        return OMERO_PASSWORD;
    }

    protected static String getPublicUsername() {
        return "public";
    }

    protected static String getUserUsername() {
        return "user";
    }

    protected static long getUserId() {
        return 5;
    }

    protected static String getUserPassword() {
        return "password";
    }

    protected static Project getProject() {
        return new Project(1);
    }

    protected static URI getProjectURI() {
        return URI.create(getWebServerURI() + "/webclient/?show=project-" + getProject().getId());
    }

    protected static String getProjectAttributeValue(int informationIndex) {
        return switch (informationIndex) {
            case 0 -> "project";
            case 1 -> String.valueOf(getProject().getId());
            case 2 -> "-";
            case 3 -> getCurrentOwner().getFullName();
            case 4 -> getCurrentGroup().getName();
            case 5 -> "1";
            default -> "";
        };
    }

    protected static Dataset getOrphanedDataset() {
        return new Dataset(2);
    }

    protected static Dataset getDataset() {
        return new Dataset(1);
    }

    protected static URI getDatasetURI() {
        return URI.create(getWebServerURI() + "/webclient/?show=dataset-" + getDataset().getId());
    }

    protected static AnnotationGroup getDatasetAnnotationGroup() {
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
                """, analysisFileId)).getAsJsonObject());
    }

    protected static String getDatasetAttributeValue(int informationIndex) {
        return switch (informationIndex) {
            case 0 -> "dataset";
            case 1 -> String.valueOf(getDataset().getId());
            case 2 -> "-";
            case 3 -> getCurrentOwner().getFullName();
            case 4 -> getCurrentGroup().getName();
            case 5 -> "9";
            default -> "";
        };
    }

    protected static List<SearchResult> getSearchResultsOnDataset() {
        return List.of(
                new SearchResult(
                        "dataset",
                        1,
                        "dataset",
                        getCurrentGroup().getName(),
                        "/webclient/?show=dataset-1",
                        null,
                        null
                ),
                new SearchResult(
                        "dataset",
                        2,
                        "orphaned_dataset",
                        getCurrentGroup().getName(),
                        "/webclient/?show=dataset-2",
                        null,
                        null
                )
        );
    }

    protected static List<Image> getImagesInDataset() {
        return List.of(
                new Image(1),
                new Image(2),
                new Image(3),
                new Image(4),
                new Image(5),
                new Image(6),
                new Image(7),
                new Image(8),
                new Image(9)
        );
    }

    protected static List<URI> getImagesUriInDataset() {
        return getImagesInDataset().stream()
                .map(image -> URI.create(getWebServerURI() + "/webclient/?show=image-" + image.getId()))
                .toList();
    }

    protected static Image getRGBImage() {
        return getImagesInDataset().get(0);
    }

    protected static URI getRGBImageURI() {
        return getImagesUriInDataset().get(0);
    }

    protected static String getRGBImageName() {
        return "rgb.tiff";
    }

    protected static int getRGBImageWidth() {
        return 256;
    }

    protected static int getRGBImageHeight() {
        return 256;
    }

    protected static PixelType getRGBImagePixelType() {
        return PixelType.UINT8;
    }

    protected static int getRGBImageNumberOfSlices() {
        return 1;
    }

    protected static int getRGBImageNumberOfChannels() {
        return 3;
    }

    protected static int getRGBImageNumberOfTimePoints() {
        return 1;
    }

    protected static double getRGBImagePixelWidthMicrons() {
        return 1.0;
    }

    protected static double getRGBImagePixelHeightMicrons() {
        return 1.0;
    }

    protected static String getRGBImageAttributeValue(int informationIndex) {
        return switch (informationIndex) {
            case 0 -> getRGBImageName();
            case 1 -> String.valueOf(getRGBImage().getId());
            case 2 -> getCurrentOwner().getFullName();
            case 3 -> getCurrentGroup().getName();
            case 4, 13 -> "-";
            case 5 -> getRGBImageWidth() + " px";
            case 6 -> getRGBImageHeight() + " px";
            case 7 -> "0.2 MB";
            case 8 -> String.valueOf(getRGBImageNumberOfSlices());
            case 9 -> String.valueOf(getRGBImageNumberOfChannels());
            case 10 -> String.valueOf(getRGBImageNumberOfTimePoints());
            case 11 -> getRGBImagePixelWidthMicrons() + " µm";
            case 12 -> getRGBImagePixelHeightMicrons() + " µm";
            case 14 -> getRGBImagePixelType().toString().toLowerCase();
            default -> "";
        };
    }

    protected static double getRGBImageRedChannelMean() {
        return 5.414;
    }

    protected static double getRGBImageRedChannelStdDev() {
        return 18.447;
    }

    protected static Image getUInt8Image() {
        return getImagesInDataset().get(1);
    }

    protected static URI getUInt8ImageURI() {
        return getImagesUriInDataset().get(1);
    }

    protected static double getUInt8ImageRedChannelMean() {
        return 7.134;
    }

    protected static double getUInt8ImageRedChannelStdDev() {
        return 24.279;
    }

    protected static Image getUInt16Image() {
        return getImagesInDataset().get(3);
    }

    protected static URI getUInt16ImageURI() {
        return getImagesUriInDataset().get(3);
    }

    protected static double getUInt16ImageRedChannelMean() {
        return 4.295;
    }

    protected static double getUInt16ImageRedChannelStdDev() {
        return 14.650;
    }

    protected static Image getInt16Image() {
        return getImagesInDataset().get(4);
    }

    protected static URI getInt16ImageURI() {
        return getImagesUriInDataset().get(4);
    }

    protected static double getInt16ImageRedChannelMean() {
        return 6.730;
    }

    protected static double getInt16ImageRedChannelStdDev() {
        return 22.913;
    }

    protected static Image getInt32Image() {
        return getImagesInDataset().get(6);
    }

    protected static URI getInt32ImageURI() {
        return getImagesUriInDataset().get(6);
    }

    protected static double getInt32ImageRedChannelMean() {
        return 4.574;
    }

    protected static double getInt32ImageRedChannelStdDev() {
        return 15.597;
    }

    protected static Image getFloat32Image() {
        return getImagesInDataset().get(7);
    }

    protected static URI getFloat32ImageURI() {
        return getImagesUriInDataset().get(7);
    }

    protected static double getFloat32ImageRedChannelMean() {
        return 8.429;
    }

    protected static double getFloat32ImageRedChannelStdDev() {
        return 28.605;
    }

    protected static ImageSettings getFloat32ImageSettings() {
        return new ImageSettings("float32.tiff", getFloat32ChannelSettings());
    }

    protected static List<ChannelSettings> getFloat32ChannelSettings() {
        return List.of(
                new ChannelSettings("0", 0, 211, Integer.parseInt("FF0000", 16)),
                new ChannelSettings("1", 0, 248, Integer.parseInt("00FF00", 16)),
                new ChannelSettings("2", 0, 184, Integer.parseInt("0000FF", 16))
        );
    }

    protected static Image getFloat64Image() {
        return getImagesInDataset().get(8);
    }

    protected static URI getFloat64ImageURI() {
        return getImagesUriInDataset().get(8);
    }

    protected static double getFloat64ImageRedChannelMean() {
        return 7.071;
    }

    protected static double getFloat64ImageRedChannelStdDev() {
        return 23.995;
    }

    protected static Image getComplexImage() {
        return new Image(10);
    }

    protected static URI getComplexImageURI() {
        return URI.create(getWebServerURI() + "/webclient/?show=image-" + getComplexImage().getId());
    }

    protected static String getComplexImageName() {
        return "complex.tiff";
    }

    protected static PixelType getComplexImagePixelType() {
        return PixelType.FLOAT32;
    }

    protected static int getComplexImageWidth() {
        return 256;
    }

    protected static int getComplexImageHeight() {
        return 256;
    }

    protected static int getComplexImageNumberOfSlices() {
        return 10;
    }

    protected static int getComplexImageNumberOfChannels() {
        return 1;
    }

    protected static int getComplexImageNumberOfTimePoints() {
        return 3;
    }

    protected static boolean isComplexImageRGB() {
        return false;
    }

    protected static double getComplexImagePixelWidthMicrons() {
        return 2.675500000484335;
    }

    protected static double getComplexImagePixelHeightMicrons() {
        return 2.675500000484335;
    }

    protected static double getComplexImagePixelZSpacingMicrons() {
        return 3.947368;
    }

    protected static String getComplexImageAttributeValue(int informationIndex) {
        return switch (informationIndex) {
            case 0 -> getComplexImageName();
            case 1 -> String.valueOf(getComplexImage().getId());
            case 2 -> getCurrentOwner().getFullName();
            case 3 -> getCurrentGroup().getName();
            case 4 -> "-";
            case 5 -> getComplexImageWidth() + " px";
            case 6 -> getComplexImageHeight() + " px";
            case 7 -> "7.5 MB";
            case 8 -> String.valueOf(getComplexImageNumberOfSlices());
            case 9 -> String.valueOf(getComplexImageNumberOfChannels());
            case 10 -> String.valueOf(getComplexImageNumberOfTimePoints());
            case 11 -> getComplexImagePixelWidthMicrons() + " µm";
            case 12 -> getComplexImagePixelHeightMicrons() + " µm";
            case 13 -> getComplexImagePixelZSpacingMicrons() + " µm";
            case 14 -> "float";
            default -> "";
        };
    }

    protected static Image getOrphanedImage() {
        return getComplexImage();
    }

    protected static Screen getScreen() {
        return new Screen(1);
    }

    protected static Plate getPlate() {
        return new Plate(1);
    }

    protected static Plate getOrphanedPlate() {
        return new Plate(2);
    }

    protected static List<Well> getWells() {
        return List.of(
                new Well(1),
                new Well(2),
                new Well(3),
                new Well(4)
        );
    }

    protected static List<Group> getGroups() {
        return List.of(
                new Group(2, "guest"),
                new Group(3, "public-data")
        );
    }

    protected static List<Group> getGroupsOfUser() {
        return List.of(
                new Group(3, "public-data")
        );
    }

    protected static List<Owner> getOwners() {
        return List.of(
                new Owner(0, "root", "", "root", "", "", "root"),
                new Owner(1, "Guest", "", "Account", "", "", "guest"),
                new Owner(2, "public", "", "access", "", "", "public")
        );
    }

    protected static Group getCurrentGroup() {
        return getGroups().get(3);
    }

    protected static Owner getCurrentOwner() {
        return getOwners().get(2);
    }

    private static void logCommandResult(Container.ExecResult result) {
        if (!result.getStdout().isBlank()) {
            logger.info(String.format("Setting up OMERO server: %s", result.getStdout()));
        }

        if (!result.getStderr().isBlank()) {
            logger.warn(String.format("Setting up OMERO server: %s", result.getStderr()));
        }
    }

    private static WebClient createValidClient(WebClient.Authentication authentication, String... args) throws ExecutionException, InterruptedException {
        WebClient webClient;
        int attempt = 0;

        do {
            webClient = WebClients.createClient(getWebServerURI(), authentication, args).get();
        } while (!webClient.getStatus().equals(WebClient.Status.SUCCESS) && ++attempt < CLIENT_CREATION_ATTEMPTS);

        if (webClient.getStatus().equals(WebClient.Status.SUCCESS)) {
            webClient.getPixelAPI(MsPixelBufferAPI.class).setPort(getMsPixelBufferApiPort(), true);
            return webClient;
        } else {
            throw new IllegalStateException("Client creation failed");
        }
    }

    private static int getMsPixelBufferApiPort() {
        return omeroWeb == null ? MS_PIXEL_BUFFER_PORT : omeroWeb.getMappedPort(MS_PIXEL_BUFFER_PORT);
    }
}
