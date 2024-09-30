package qupath.ext.omero.core.apis;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.*;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.entities.annotations.AnnotationGroup;
import qupath.ext.omero.core.entities.annotations.MapAnnotation;
import qupath.ext.omero.core.entities.image.ChannelSettings;
import qupath.ext.omero.core.entities.image.ImageSettings;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.OrphanedFolder;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.Server;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.*;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.search.SearchQuery;
import qupath.ext.omero.core.entities.search.SearchResult;
import qupath.ext.omero.core.entities.shapes.Line;
import qupath.ext.omero.core.entities.shapes.Rectangle;
import qupath.ext.omero.core.entities.shapes.Shape;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TestApisHandler extends OmeroServer {

    abstract static class GenericClient {

        protected static WebClient client;
        protected static ApisHandler apisHandler;
        protected static UserType userType;

        @AfterAll
        static void removeClient() {
            WebClients.removeClient(client);
        }

        @Test
        void Check_UInt8_Pixel_Type() {
            PixelType expectedPixelType = PixelType.UINT8;

            PixelType pixelType = ApisHandler.getPixelType("uint8").orElse(null);

            Assertions.assertEquals(expectedPixelType, pixelType);
        }

        @Test
        void Check_Int8_Pixel_Type() {
            PixelType expectedPixelType = PixelType.INT8;

            PixelType pixelType = ApisHandler.getPixelType("int8").orElse(null);

            Assertions.assertEquals(expectedPixelType, pixelType);
        }

        @Test
        void Check_UInt16_Pixel_Type() {
            PixelType expectedPixelType = PixelType.UINT16;

            PixelType pixelType = ApisHandler.getPixelType("uint16").orElse(null);

            Assertions.assertEquals(expectedPixelType, pixelType);
        }

        @Test
        void Check_Int16_Pixel_Type() {
            PixelType expectedPixelType = PixelType.INT16;

            PixelType pixelType = ApisHandler.getPixelType("int16").orElse(null);

            Assertions.assertEquals(expectedPixelType, pixelType);
        }

        @Test
        void Check_UInt32_Pixel_Type() {
            PixelType expectedPixelType = PixelType.UINT32;

            PixelType pixelType = ApisHandler.getPixelType("uint32").orElse(null);

            Assertions.assertEquals(expectedPixelType, pixelType);
        }

        @Test
        void Check_Int32_Pixel_Type() {
            PixelType expectedPixelType = PixelType.INT32;

            PixelType pixelType = ApisHandler.getPixelType("int32").orElse(null);

            Assertions.assertEquals(expectedPixelType, pixelType);
        }

        @Test
        void Check_Float_Pixel_Type() {
            PixelType expectedPixelType = PixelType.FLOAT32;

            PixelType pixelType = ApisHandler.getPixelType("float").orElse(null);

            Assertions.assertEquals(expectedPixelType, pixelType);
        }

        @Test
        void Check_Double_Pixel_Type() {
            PixelType expectedPixelType = PixelType.FLOAT64;

            PixelType pixelType = ApisHandler.getPixelType("double").orElse(null);

            Assertions.assertEquals(expectedPixelType, pixelType);
        }

        @Test
        void Check_Invalid_Pixel_Type() {
            Optional<PixelType> pixelType = ApisHandler.getPixelType("invalid");

            Assertions.assertTrue(pixelType.isEmpty());
        }

        @Test
        void Check_Web_Server_URI() {
            URI expectedWebServerURI = URI.create(OmeroServer.getWebServerURI());

            URI webServerURI = apisHandler.getWebServerURI();

            Assertions.assertEquals(expectedWebServerURI, webServerURI);
        }

        @Test
        void Check_Server_URI() {
            String expectedServerURI = OmeroServer.getServerURI();

            String serverURI = apisHandler.getServerURI();

            Assertions.assertEquals(expectedServerURI, serverURI);
        }

        @Test
        void Check_Server_Port() {
            int expectedServerPort = OmeroServer.getServerPort();

            int serverPort = apisHandler.getServerPort();

            Assertions.assertEquals(expectedServerPort, serverPort);
        }

        @Test
        void Check_Can_Skip_Authentication() {
            boolean canSkipAuthentication = apisHandler.canSkipAuthentication();

            Assertions.assertTrue(canSkipAuthentication);
        }

        @Test
        void Check_Images_URI_Of_Dataset() throws ExecutionException, InterruptedException {
            Dataset dataset = OmeroServer.getDatasets(userType).getLast();
            long datasetID = dataset.getId();
            List<URI> expectedURIs = OmeroServer.getImagesInDataset(dataset).stream().map(OmeroServer::getImageURI).toList();

            List<URI> uris = apisHandler.getImagesURIOfDataset(datasetID).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedURIs, uris);
        }

        @Test
        void Check_Image_URI_Of_Invalid_Dataset() throws ExecutionException, InterruptedException {
            long datasetID = -1;
            List<URI> expectedURIs = List.of();

            List<URI> uris = apisHandler.getImagesURIOfDataset(datasetID).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedURIs, uris);
        }

        @Test
        void Check_Image_URI_Of_Project() throws ExecutionException, InterruptedException {
            Project project = OmeroServer.getProjects(userType).getLast();
            long projectID = project.getId();
            List<URI> expectedURIs = OmeroServer.getDatasetsInProject(project).stream()
                    .map(OmeroServer::getImagesInDataset)
                    .flatMap(List::stream)
                    .map(OmeroServer::getImageURI)
                    .toList();

            List<URI> uris = apisHandler.getImagesURIOfProject(projectID).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedURIs, uris);
        }

        @Test
        void Check_Image_URI_Of_Invalid_Project() throws ExecutionException, InterruptedException {
            long projectID = -1;
            List<URI> expectedURIs = List.of();

            List<URI> uris = apisHandler.getImagesURIOfProject(projectID).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedURIs, uris);
        }

        @Test
        void Check_Image_URI() {
            Image image = OmeroServer.getRGBImage(userType);
            String expectedURI = OmeroServer.getImageURI(image).toString();

            String uri = apisHandler.getItemURI(image);

            Assertions.assertEquals(expectedURI, uri);
        }

        @Test
        void Check_Dataset_URI() {
            Dataset dataset = OmeroServer.getDatasets(userType).getLast();
            String expectedURI = OmeroServer.getDatasetURI(dataset).toString();

            String uri = apisHandler.getItemURI(dataset);

            Assertions.assertEquals(expectedURI, uri);
        }

        @Test
        void Check_Project_URI() {
            Project project = OmeroServer.getProjects(userType).getLast();
            String expectedURI = OmeroServer.getProjectURI(project).toString();

            String uri = apisHandler.getItemURI(project);

            Assertions.assertEquals(expectedURI, uri);
        }

        @Test
        void Check_Invalid_Entity_URI() {
            ServerEntity serverEntity = new ServerEntityImplementation();

            Assertions.assertThrows(IllegalArgumentException.class, () ->
                    apisHandler.getItemURI(serverEntity)
            );
        }

        @Test
        void Check_Ping() throws ExecutionException, InterruptedException {
            boolean pingSucceeded = apisHandler.ping().get();

            Assertions.assertTrue(pingSucceeded);
        }

        @Test
        void Check_Orphaned_Images_Id() throws ExecutionException, InterruptedException {
            List<Long> expectedIds = OmeroServer.getOrphanedImages(userType).stream().map(Image::getId).toList();

            List<Long> ids = apisHandler.getOrphanedImagesIds().get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedIds, ids);
        }

        @Test
        void Check_Groups() throws ExecutionException, InterruptedException {
            long userId = OmeroServer.getConnectedOwner(userType).id();
            List<Group> expectedGroups = OmeroServer.getGroups(userType);

            List<Group> groups = apisHandler.getGroups(userId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedGroups, groups);
        }

        @Test
        void Check_Projects() throws ExecutionException, InterruptedException {
            List<Project> expectedProjects = OmeroServer.getProjects(userType);

            List<Project> projects = apisHandler.getProjects().get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedProjects, projects);
        }

        @Test
        void Check_Orphaned_Datasets() throws ExecutionException, InterruptedException {
            List<Dataset> expectedOrphanedDatasets = OmeroServer.getOrphanedDatasets(userType);

            List<Dataset> orphanedDatasets = apisHandler.getOrphanedDatasets().get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedOrphanedDatasets, orphanedDatasets);
        }

        @Test
        void Check_Datasets() throws ExecutionException, InterruptedException {
            Project project = OmeroServer.getProjects(userType).getLast();
            long projectID = project.getId();
            List<Dataset> expectedDatasets = OmeroServer.getDatasetsInProject(project);

            List<Dataset> datasets = apisHandler.getDatasets(projectID).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedDatasets, datasets);
        }

        @Test
        void Check_Datasets_Of_Invalid_Project() throws ExecutionException, InterruptedException {
            long invalidProjectID = -1;
            List<Dataset> expectedDatasets = List.of();

            List<Dataset> datasets = apisHandler.getDatasets(invalidProjectID).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedDatasets, datasets);
        }

        @Test
        void Check_Images() throws ExecutionException, InterruptedException {
            Dataset dataset = OmeroServer.getDatasets(userType).getLast();
            long datasetID = dataset.getId();
            List<Image> expectedImages = OmeroServer.getImagesInDataset(dataset);

            List<Image> images = apisHandler.getImages(datasetID).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedImages, images);
        }

        @Test
        void Check_Images_Of_Invalid_Dataset() throws ExecutionException, InterruptedException {
            long invalidDatasetID = -1;
            List<Image> expectedImages = List.of();

            List<Image> images = apisHandler.getImages(invalidDatasetID).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedImages, images);
        }

        @Test
        void Check_Image() throws ExecutionException, InterruptedException {
            Image expectedImage = OmeroServer.getRGBImage(userType);
            long imageID = expectedImage.getId();

            Image image = apisHandler.getImage(imageID).get().orElse(null);

            Assertions.assertEquals(expectedImage, image);
        }

        @Test
        void Check_Image_With_Invalid_ID() throws ExecutionException, InterruptedException {
            long imageID = -1;

            Image image = apisHandler.getImage(imageID).get().orElse(null);

            Assertions.assertNull(image);
        }

        @Test
        void Check_Number_Of_Orphaned_Image() throws ExecutionException, InterruptedException {
            int expectedNumberOfOrphanedImages = OmeroServer.getOrphanedImages(userType).size();

            int numberOfOrphanedImages = apisHandler.getNumberOfOrphanedImages().get();

            Assertions.assertEquals(expectedNumberOfOrphanedImages, numberOfOrphanedImages);
        }

        @Test
        void Check_Loading_Orphaned_Images() throws InterruptedException {
            List<Image> images = new ArrayList<>();
            List<Image> expectedImages = OmeroServer.getOrphanedImages(userType);

            apisHandler.populateOrphanedImagesIntoList(images);
            while (apisHandler.areOrphanedImagesLoading().get()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedImages, images);
        }

        @Test
        void Check_Number_Of_Loaded_Orphaned_Images() throws InterruptedException {
            List<Image> images = new ArrayList<>();
            int expectedNumberOfOrphanedImages = OmeroServer.getOrphanedImages(userType).size();

            apisHandler.populateOrphanedImagesIntoList(images);
            while (apisHandler.areOrphanedImagesLoading().get()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }
            int numberOfOrphanedImages = apisHandler.getNumberOfOrphanedImagesLoaded().get();

            Assertions.assertEquals(expectedNumberOfOrphanedImages, numberOfOrphanedImages);
        }

        @Test
        void Check_Screens() throws ExecutionException, InterruptedException {
            List<Screen> expectedScreens = OmeroServer.getScreens(userType);

            List<Screen> screens = apisHandler.getScreens().get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedScreens, screens);
        }

        @Test
        void Check_Orphaned_Plates() throws ExecutionException, InterruptedException {
            List<Plate> expectedOrphanedPlates = OmeroServer.getOrphanedPlates(userType);

            List<Plate> orphanedPlates = apisHandler.getOrphanedPlates().get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedOrphanedPlates, orphanedPlates);
        }

        @Test
        void Check_Plates() throws ExecutionException, InterruptedException {
            Screen screen = OmeroServer.getScreens(userType).getLast();
            long screenId = screen.getId();
            List<Plate> expectedPlates = OmeroServer.getPlatesInScreen(screen);

            List<Plate> plates = apisHandler.getPlates(screenId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedPlates, plates);
        }

        @Test
        void Check_Plate_Acquisitions() throws ExecutionException, InterruptedException {
            Screen screen = OmeroServer.getScreens(userType).getLast();
            long screenId = screen.getId();
            List<PlateAcquisition> expectedPlateAcquisitions = OmeroServer.getPlateAcquisitionsInScreen();

            List<PlateAcquisition> plateAcquisitions = apisHandler.getPlateAcquisitions(screenId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedPlateAcquisitions, plateAcquisitions);
        }

        @Test
        void Check_Wells() throws ExecutionException, InterruptedException {
            Screen screen = OmeroServer.getScreens(userType).getLast();
            Plate plate = OmeroServer.getPlatesInScreen(screen).getLast();
            long plateId = plate.getId();
            List<Well> expectedWells = OmeroServer.getWellsInPlate(plate);

            List<Well> wells = apisHandler.getWellsFromPlate(plateId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedWells, wells);
        }

        @Test
        void Check_Annotations() throws ExecutionException, InterruptedException {
            Dataset dataset = OmeroServer.getDatasets(userType).getFirst();
            AnnotationGroup expectedAnnotationGroup = OmeroServer.getAnnotationsInDataset(dataset);

            AnnotationGroup annotationGroup = apisHandler.getAnnotations(
                    dataset.getId(),
                    Dataset.class
            ).get().orElse(null);

            Assertions.assertEquals(expectedAnnotationGroup, annotationGroup);
        }

        @Test
        void Check_Search() throws ExecutionException, InterruptedException {
            SearchQuery searchQuery = new SearchQuery(
                    "dataset",
                    false,
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    Group.getAllGroupsGroup(),
                    Owner.getAllMembersOwner()
            );
            List<SearchResult> expectedResults = OmeroServer.getSearchResultsOnDataset(userType);

            List<SearchResult> searchResults = apisHandler.getSearchResults(searchQuery).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedResults, searchResults);
        }

        @Test
        abstract void Check_Key_Value_Pairs_Sent() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Key_Value_Pairs_Sent_When_Existing_Deleted() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Key_Value_Pairs_Sent_When_Existing_Not_Deleted() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Key_Value_Pairs_Sent_When_Existing_Replaced() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Key_Value_Pairs_Sent_When_Existing_Not_Replaced() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Image_Name_Can_Be_Changed() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Image_Name_Changed() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Channel_Names_Can_Be_Changed() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Channel_Names_Changed() throws ExecutionException, InterruptedException;

        @Test
        void Check_Dataset_Icon() throws ExecutionException, InterruptedException {
            Class<? extends RepositoryEntity> type = Dataset.class;

            BufferedImage icon = apisHandler.getOmeroIcon(type).get().orElse(null);

            Assertions.assertNotNull(icon);
        }

        @Test
        void Check_Image_Icon() throws ExecutionException, InterruptedException {
            Class<? extends RepositoryEntity> type = Image.class;

            BufferedImage icon = apisHandler.getOmeroIcon(type).get().orElse(null);

            Assertions.assertNotNull(icon);
        }

        @Test
        void Check_Orphaned_Folder_Icon() throws ExecutionException, InterruptedException {
            Class<? extends RepositoryEntity> type = OrphanedFolder.class;

            BufferedImage icon = apisHandler.getOmeroIcon(type).get().orElse(null);

            Assertions.assertNotNull(icon);
        }

        @Test
        void Check_Project_Icon() throws ExecutionException, InterruptedException {
            Class<? extends RepositoryEntity> type = Project.class;

            BufferedImage icon = apisHandler.getOmeroIcon(type).get().orElse(null);

            Assertions.assertNotNull(icon);
        }

        @Test
        void Check_Server_Icon() throws ExecutionException, InterruptedException {
            Class<? extends RepositoryEntity> type = Server.class;

            BufferedImage icon = apisHandler.getOmeroIcon(type).get().orElse(null);

            Assertions.assertNull(icon);    // there is no icon for the server
        }

        @Test
        void Check_Image_Thumbnail() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getRGBImage(userType).getId();

            BufferedImage image = apisHandler.getThumbnail(imageId).get().orElse(null);

            Assertions.assertNotNull(image);
        }

        @Test
        void Check_Image_Thumbnail_With_Specific_Size() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getRGBImage(userType).getId();
            int size = 30;

            BufferedImage image = apisHandler.getThumbnail(imageId, size).get().orElse(null);

            Assertions.assertNotNull(image);
            Assertions.assertEquals(size, Math.max(image.getWidth(), image.getHeight()));
        }

        @Test
        void Check_Image_Thumbnail_With_Invalid_Image_ID() throws ExecutionException, InterruptedException {
            long invalidImageID = -1;

            BufferedImage image = apisHandler.getThumbnail(invalidImageID).get().orElse(null);

            Assertions.assertNull(image);
        }

        @Test
        void Check_Image_Metadata() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getComplexImage(userType);
            long imageId = image.getId();
            ImageServerMetadata expectedMetadata = OmeroServer.getImageMetadata(image);

            ImageServerMetadata metadata = apisHandler.getImageMetadata(imageId).get().orElse(null);

            Assertions.assertNotNull(metadata);
            Assertions.assertEquals(expectedMetadata, metadata);
        }

        @Test
        void Check_Image_Metadata_With_Invalid_Image_ID() throws ExecutionException, InterruptedException {
            long invalidImageID = -1;

            Optional<ImageServerMetadata> metadata = apisHandler.getImageMetadata(invalidImageID).get();

            Assertions.assertTrue(metadata.isEmpty());
        }

        @Test
        abstract void Check_Channel_Colors_Can_Be_Changed() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Channel_Colors_Changed() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Channel_Display_Ranges_Can_Be_Changed() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Channel_Display_Ranges_Changed() throws ExecutionException, InterruptedException;

        @Test
        void Check_Get_ROIs_With_Invalid_Image_ID() throws ExecutionException, InterruptedException {
            long invalidImageID = -1;
            List<Shape> expectedROIs = List.of();

            List<Shape> rois = apisHandler.getROIs(invalidImageID).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedROIs, rois);
        }

        @Test
        abstract void Check_Write_ROIs() throws ExecutionException, InterruptedException;

        @Test
        void Check_Image_Settings() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getFloat32Image(userType);
            ImageSettings expectedImageSettings = OmeroServer.getFloat32ImageSettings();

            ImageSettings imageSettings = apisHandler.getImageSettings(image.getId()).get().orElse(null);

            Assertions.assertEquals(expectedImageSettings, imageSettings);
        }

        @Test
        abstract void Check_Attachments_Sent() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Existing_Attachments_Deleted() throws ExecutionException, InterruptedException;
    }

    @Nested
    class UnauthenticatedClient extends GenericClient {
        // Unauthenticated user has read-only access,
        // so all write functions will fail

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            userType = UserType.PUBLIC;
            client = OmeroServer.createClient(userType);
            apisHandler = client.getApisHandler();
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> keyValues = Map.of(
                    "A", "B",
                    "C", "D"
            );

            boolean status = apisHandler.sendKeyValuePairs(image.getId(), keyValues, true, true).get();

            Assertions.assertFalse(status);
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Deleted() {
            // Empty because key values can't be sent, see Check_Key_Value_Pairs_Sent
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Not_Deleted() {
            // Empty because key values can't be sent, see Check_Key_Value_Pairs_Sent
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Replaced() {
            // Empty because key values can't be sent, see Check_Key_Value_Pairs_Sent
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Not_Replaced() {
            // Empty because key values can't be sent, see Check_Key_Value_Pairs_Sent
        }

        @Test
        @Override
        void Check_Image_Name_Can_Be_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            String newImageName = "new_name";

            boolean status = apisHandler.changeImageName(image.getId(), newImageName).get();

            Assertions.assertFalse(status);
        }

        @Test
        @Override
        void Check_Image_Name_Changed() {
            // Empty because name can't be changed, see Check_Image_Name_Can_Be_Changed
        }

        @Test
        @Override
        void Check_Channel_Names_Can_Be_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<String> newChannelsName = List.of("New channel 1", "New channel 2", "New channel 3");

            boolean status = apisHandler.changeChannelNames(image.getId(), newChannelsName).get();

            Assertions.assertFalse(status);
        }

        @Test
        @Override
        void Check_Channel_Names_Changed() {
            // Empty because channel names can't be changed, see Check_Channels_Names_Can_Be_Changed
        }

        @Test
        @Override
        void Check_Channel_Colors_Can_Be_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<Integer> channelColors = List.of(
                    Integer.parseInt("00FFFF", 16),
                    Integer.parseInt("FF00FF", 16),
                    Integer.parseInt("FFFF00", 16)
            );

            boolean status = apisHandler.changeChannelColors(image.getId(), channelColors).get();

            Assertions.assertFalse(status);
        }

        @Test
        @Override
        void Check_Channel_Colors_Changed() {
            // Empty because channel colors can't be changed, see Check_Channel_Colors_Can_Be_Changed
        }

        @Test
        @Override
        void Check_Channel_Display_Ranges_Can_Be_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<ChannelSettings> channelSettings = List.of(
                    new ChannelSettings(0.45, 100.654),
                    new ChannelSettings(50, 200),
                    new ChannelSettings(75.64, 80.9807)
            );

            boolean status = apisHandler.changeChannelDisplayRanges(image.getId(), channelSettings).get();

            Assertions.assertFalse(status);
        }

        @Test
        @Override
        void Check_Channel_Display_Ranges_Changed() {
            // Empty because channel display ranges can't be changed, see Check_Channel_Display_Ranges_Can_Be_Changed
        }

        @Test
        @Override
        void Check_Write_ROIs() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getAnnotableImage(userType).getId();
            List<Shape> rois = List.of(new Rectangle(10, 10, 100, 100), new Line(20, 20, 50, 50));

            boolean success = apisHandler.writeROIs(imageId, rois, true).get();

            Assertions.assertFalse(success);
        }

        @Test
        @Override
        void Check_Attachments_Sent() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getAnnotableImage(userType);

            boolean success = apisHandler.sendAttachment(
                    image.getId(),
                    image.getClass(),
                    "annotations.csv",
                    """
                    id,value
                    1,test1
                    2,test2
                    3,test3
                    """
            ).get();

            Assertions.assertFalse(success);
        }

        @Test
        @Override
        void Check_Existing_Attachments_Deleted() {
            // Empty because attachments can't be changed, see Check_Attachments_Sent
        }
    }

    @Nested
    class AuthenticatedClient extends GenericClient {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            userType = UserType.USER;
            client = OmeroServer.createClient(userType);
            apisHandler = client.getApisHandler();
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> keyValues = Map.of(
                    "A", "B",
                    "C", "D"
            );

            boolean status = apisHandler.sendKeyValuePairs(image.getId(), keyValues, true, true).get();

            Assertions.assertTrue(status);
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Deleted() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> existingKeyValues = Map.of(
                    "existingKey", "existingValue"
            );
            apisHandler.sendKeyValuePairs(image.getId(), existingKeyValues, true, true).get();
            Map<String, String> keyValuesToSend = Map.of(
                    "A", "B",
                    "C", "D"
            );
            Map<String, String> expectedKeyValues = Map.of(
                    "A", "B",
                    "C", "D"
            );

            apisHandler.sendKeyValuePairs(image.getId(), keyValuesToSend, true, true).get();

            Map<String, String> keyValues = apisHandler.getAnnotations(image.getId(), Image.class).get()
                    .map(annotationGroup -> annotationGroup.getAnnotationsOfClass(MapAnnotation.class))
                    .map(annotations -> annotations.stream()
                            .map(MapAnnotation::getValues)
                            .flatMap (map -> map.entrySet().stream())
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (value1, value2) -> value1
                            ))
                    )
                    .orElse(Map.of());
            Assertions.assertEquals(expectedKeyValues, keyValues);
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Not_Deleted() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> existingKeyValues = Map.of(
                    "existingKey", "existingValue"
            );
            apisHandler.sendKeyValuePairs(image.getId(), existingKeyValues, true, true).get();
            Map<String, String> keyValuesToSend = Map.of(
                    "A", "B",
                    "C", "D"
            );
            Map<String, String> expectedKeyValues = Map.of(
                    "A", "B",
                    "C", "D",
                    "existingKey", "existingValue"
            );

            apisHandler.sendKeyValuePairs(image.getId(), keyValuesToSend, true, false).get();

            Map<String, String> keyValues = apisHandler.getAnnotations(image.getId(), Image.class).get()
                    .map(annotationGroup -> annotationGroup.getAnnotationsOfClass(MapAnnotation.class))
                    .map(annotations -> annotations.stream()
                            .map(MapAnnotation::getValues)
                            .flatMap (map -> map.entrySet().stream())
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (value1, value2) -> value1
                            ))
                    )
                    .orElse(Map.of());
            Assertions.assertEquals(expectedKeyValues, keyValues);
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Replaced() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> existingKeyValues = Map.of(
                    "A", "existingValue"
            );
            apisHandler.sendKeyValuePairs(image.getId(), existingKeyValues, true, true).get();
            Map<String, String> keyValuesToSend = Map.of(
                    "A", "B",
                    "C", "D"
            );
            Map<String, String> expectedKeyValues = Map.of(
                    "A", "B",
                    "C", "D"
            );

            apisHandler.sendKeyValuePairs(image.getId(), keyValuesToSend, true, true).get();

            Map<String, String> keyValues = apisHandler.getAnnotations(image.getId(), Image.class).get()
                    .map(annotationGroup -> annotationGroup.getAnnotationsOfClass(MapAnnotation.class))
                    .map(annotations -> annotations.stream()
                            .map(MapAnnotation::getValues)
                            .flatMap (map -> map.entrySet().stream())
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (value1, value2) -> value1
                            ))
                    )
                    .orElse(Map.of());
            Assertions.assertEquals(expectedKeyValues, keyValues);
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Not_Replaced() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> existingKeyValues = Map.of(
                    "A", "existingValue"
            );
            apisHandler.sendKeyValuePairs(image.getId(), existingKeyValues, true, true).get();
            Map<String, String> keyValuesToSend = Map.of(
                    "A", "B",
                    "C", "D"
            );
            Map<String, String> expectedKeyValues = Map.of(
                    "A", "existingValue",
                    "C", "D"
            );

            apisHandler.sendKeyValuePairs(image.getId(), keyValuesToSend, false, false).get();

            Map<String, String> keyValues = apisHandler.getAnnotations(image.getId(), Image.class).get()
                    .map(annotationGroup -> annotationGroup.getAnnotationsOfClass(MapAnnotation.class))
                    .map(annotations -> annotations.stream()
                            .map(MapAnnotation::getValues)
                            .flatMap (map -> map.entrySet().stream())
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (value1, value2) -> value1
                            ))
                    )
                    .orElse(Map.of());
            Assertions.assertEquals(expectedKeyValues, keyValues);
        }

        @Test
        @Override
        void Check_Image_Name_Can_Be_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            String newImageName = "new_name";

            boolean status = apisHandler.changeImageName(image.getId(), newImageName).get();

            Assertions.assertTrue(status);

            // Reset image name
            apisHandler.changeImageName(image.getId(), OmeroServer.getImageMetadata(image).getName()).get();
        }

        @Test
        @Override
        void Check_Image_Name_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            String expectedNewImageName = "new_name";

            apisHandler.changeImageName(image.getId(), expectedNewImageName).get();

            String newImageName = Objects.requireNonNull(apisHandler.getImageSettings(image.getId()).get().orElse(null)).getName();
            Assertions.assertEquals(expectedNewImageName, newImageName);

            // Reset image name
            apisHandler.changeImageName(image.getId(), OmeroServer.getImageMetadata(image).getName()).get();
        }

        @Test
        @Override
        void Check_Channel_Names_Can_Be_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<String> newChannelsName = List.of("New channel 1");

            boolean status = apisHandler.changeChannelNames(image.getId(), newChannelsName).get();

            Assertions.assertTrue(status);

            // Reset channel names
            apisHandler.changeChannelNames(
                    image.getId(),
                    OmeroServer.getModifiableImageChannelSettings().stream().map(ChannelSettings::getName).toList()
            ).get();
        }

        @Test
        @Override
        void Check_Channel_Names_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<String> expectedNewChannelNames = List.of("New channel 1");

            apisHandler.changeChannelNames(image.getId(), expectedNewChannelNames).get();

            List<String> newChannelNames = Objects.requireNonNull(apisHandler.getImageSettings(image.getId()).get().orElse(null))
                    .getChannelSettings()
                    .stream()
                    .map(ChannelSettings::getName)
                    .toList();
            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedNewChannelNames, newChannelNames);

            // Reset channel names
            apisHandler.changeChannelNames(
                    image.getId(),
                    OmeroServer.getModifiableImageChannelSettings().stream().map(ChannelSettings::getName).toList()
            ).get();
        }

        @Test
        @Override
        void Check_Channel_Colors_Can_Be_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<Integer> channelColors = List.of(
                    Integer.parseInt("00FFFF", 16)
            );

            boolean status = apisHandler.changeChannelColors(image.getId(), channelColors).get();

            Assertions.assertTrue(status);

            // Reset channel colors
            apisHandler.changeChannelColors(image.getId(), OmeroServer.getModifiableImageChannelSettings().stream()
                    .map(ChannelSettings::getRgbColor)
                    .toList()
            ).get();
        }

        @Test
        @Override
        void Check_Channel_Colors_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<Integer> expectedChannelColors = List.of(
                    Integer.parseInt("00FFFF", 16)
            );

            apisHandler.changeChannelColors(image.getId(), expectedChannelColors).get();

            List<Integer> channelColors = Objects.requireNonNull(apisHandler.getImageSettings(image.getId()).get().orElse(null))
                    .getChannelSettings()
                    .stream()
                    .map(ChannelSettings::getRgbColor)
                    .toList();
            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChannelColors, channelColors);

            // Reset channel colors
            apisHandler.changeChannelColors(image.getId(), OmeroServer.getModifiableImageChannelSettings().stream()
                    .map(ChannelSettings::getRgbColor)
                    .toList()
            ).get();
        }

        @Test
        @Override
        void Check_Channel_Display_Ranges_Can_Be_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<ChannelSettings> channelSettings = List.of(
                    new ChannelSettings(0.45, 100.654)
            );

            boolean status = apisHandler.changeChannelDisplayRanges(image.getId(), channelSettings).get();

            Assertions.assertTrue(status);

            // Reset channel display ranges
            apisHandler.changeChannelDisplayRanges(image.getId(), OmeroServer.getModifiableImageChannelSettings()).get();
        }

        @Test
        @Override
        void Check_Channel_Display_Ranges_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<ChannelSettings> expectedChannelSettings = List.of(
                    new ChannelSettings(
                            OmeroServer.getModifiableImageChannelSettings().get(0).getName(),
                            0.45,
                            100.654,
                            OmeroServer.getModifiableImageChannelSettings().get(0).getRgbColor()
                    )
            );

            apisHandler.changeChannelDisplayRanges(image.getId(), expectedChannelSettings).get();

            List<ChannelSettings> channelSettings = Objects.requireNonNull(apisHandler.getImageSettings(image.getId()).get().orElse(null)).getChannelSettings();
            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChannelSettings, channelSettings);

            // Reset channel display ranges
            apisHandler.changeChannelDisplayRanges(image.getId(), OmeroServer.getModifiableImageChannelSettings()).get();
        }

        @Test
        @Override
        void Check_Write_ROIs() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getAnnotableImage(userType).getId();
            List<Shape> rois = List.of(new Rectangle(10, 10, 100, 100), new Line(20, 20, 50, 50));

            boolean success = apisHandler.writeROIs(imageId, rois, true).get();

            Assertions.assertTrue(success);
        }

        @Test
        @Override
        void Check_Attachments_Sent() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getAnnotableImage(userType);

            boolean success = apisHandler.sendAttachment(
                    image.getId(),
                    image.getClass(),
                    "annotations.csv",
                    """
                    id,value
                    1,test1
                    2,test2
                    3,test3
                    """
            ).get();

            Assertions.assertTrue(success);
        }

        @Test
        @Override
        void Check_Existing_Attachments_Deleted() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getAnnotableImage(userType);
            apisHandler.sendAttachment(image.getId(), image.getClass(),"annotations1.csv", "test1").get();
            apisHandler.sendAttachment(image.getId(), image.getClass(),"annotations2.csv", "test2").get();
            apisHandler.sendAttachment(image.getId(), image.getClass(),"annotations3.csv", "test3").get();

            boolean success = apisHandler.deleteAttachments(image.getId(), image.getClass()).get();

            Assertions.assertTrue(success);
        }
    }

    private static class ServerEntityImplementation extends ServerEntity {

        @Override
        public boolean hasChildren() {
            return false;
        }

        @Override
        public ObservableList<? extends RepositoryEntity> getChildren() {
            return null;
        }

        @Override
        public ReadOnlyStringProperty getLabel() {
            return null;
        }

        @Override
        public boolean isPopulatingChildren() {
            return false;
        }

        @Override
        public String getAttributeName(int informationIndex) {
            return null;
        }

        @Override
        public String getAttributeValue(int informationIndex) {
            return null;
        }

        @Override
        public int getNumberOfAttributes() {
            return 0;
        }
    }
}


