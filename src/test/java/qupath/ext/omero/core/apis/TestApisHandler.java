package qupath.ext.omero.core.apis;

import com.google.common.util.concurrent.UncheckedExecutionException;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.AfterAll;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.entities.Namespace;
import qupath.ext.omero.core.entities.annotations.AnnotationGroup;
import qupath.ext.omero.core.entities.annotations.MapAnnotation;
import qupath.ext.omero.core.entities.image.ChannelSettings;
import qupath.ext.omero.core.entities.image.ImageSettings;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.OrphanedFolder;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.Server;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Well;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TestApisHandler extends OmeroServer {

    abstract static class GenericClient {

        protected static Client client;
        protected static ApisHandler apisHandler;
        protected static UserType userType;

        @AfterAll
        static void removeClient() throws Exception {
            if (client != null) {
                client.close();
            }
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
        void Check_Credentials() {
            Credentials expectedCredentials = OmeroServer.getCredentials(userType);

            Credentials credentials = apisHandler.getCredentials();

            Assertions.assertEquals(expectedCredentials, credentials);
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
        void Check_User_Id() throws ExecutionException, InterruptedException {
            long expectedId = OmeroServer.getConnectedOwner(userType).id();

            long id = apisHandler.getUserId().get();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        abstract void Check_Default_Group();

        @Test
        abstract void Check_Session_Uuid();

        @Test
        abstract void Check_Is_Admin();

        @Test
        void Check_Is_Connected_User_Owner_Of_Group() {
            long groupId = OmeroServer.getDefaultGroup(userType).getId();

            Assertions.assertFalse(apisHandler.isConnectedUserOwnerOfGroup(groupId));
        }

        @Test
        void Check_Base_URL_Reachable() {
            URI serverURI = URI.create(OmeroServer.getWebServerURI());

            Assertions.assertDoesNotThrow(() -> apisHandler.isLinkReachable(serverURI, RequestSender.RequestType.GET).get());
        }

        @Test
        void Check_OMERO_Id_On_Project() {
            long expectedID = 201;
            URI uri = URI.create(String.format(
                    "http://localhost:4080/webclient/?show=project-%d",
                    expectedID
            ));

            long id = ApisHandler.parseEntityId(uri).orElse(-1L);

            Assertions.assertEquals(expectedID, id);
        }

        @Test
        void Check_OMERO_Id_On_Dataset() {
            long expectedID = 1157;
            URI uri = URI.create(String.format("http://localhost:4080/webclient/?show=dataset-%d", expectedID));

            long id = ApisHandler.parseEntityId(uri).orElse(-1L);

            Assertions.assertEquals(expectedID, id);
        }

        @Test
        void Check_OMERO_Id_On_Webclient_Image() {
            long expectedID = 12546;
            URI uri = URI.create(String.format("http://localhost:4080/webclient/?show=image-%d", expectedID));

            long id = ApisHandler.parseEntityId(uri).orElse(-1L);

            Assertions.assertEquals(expectedID, id);
        }

        @Test
        void Check_OMERO_Id_On_Webclient_Image_Alternate() {
            long expectedID = 12546;
            URI uri = URI.create(String.format("http://localhost:4080/webclient/img_detail/%d/?dataset=1157", expectedID));

            long id = ApisHandler.parseEntityId(uri).orElse(-1L);

            Assertions.assertEquals(expectedID, id);
        }

        @Test
        void Check_OMERO_Id_On_WebGateway_Image() {
            long expectedID = 12546;
            URI uri = URI.create(String.format("http://localhost:4080/webgateway/img_detail/%d/?dataset=1157", expectedID));

            long id = ApisHandler.parseEntityId(uri).orElse(-1L);

            Assertions.assertEquals(expectedID, id);
        }

        @Test
        void Check_OMERO_Id_On_IViewer_Image() {
            long expectedID = 12546;
            URI uri = URI.create(String.format("http://localhost:4080/iviewer/?images=%d&dataset=1157", expectedID));

            long id = ApisHandler.parseEntityId(uri).orElse(-1L);

            Assertions.assertEquals(expectedID, id);
        }

        @Test
        void Check_Image_URIs_From_Dataset_URI() throws ExecutionException, InterruptedException {
            Dataset dataset = OmeroServer.getDatasets(userType).getLast();
            URI datasetUri = OmeroServer.getDatasetURI(dataset);
            List<URI> expectedImageUris = OmeroServer.getImagesInDataset(dataset).stream()
                    .map(OmeroServer::getImageURI)
                    .toList();

            List<URI> imageUris = apisHandler.getImagesURIFromEntityURI(datasetUri).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedImageUris, imageUris);
        }

        @Test
        void Check_Image_URIs_From_Project_URI() throws ExecutionException, InterruptedException {
            Project project = OmeroServer.getProjects(userType).getLast();
            URI projectUri = OmeroServer.getProjectURI(project);
            List<URI> expectedImageUris = OmeroServer.getDatasetsInProject(project).stream()
                    .map(OmeroServer::getImagesInDataset)
                    .flatMap(List::stream)
                    .map(OmeroServer::getImageURI)
                    .toList();

            List<URI> imageUris = apisHandler.getImagesURIFromEntityURI(projectUri).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedImageUris, imageUris);
        }

        @Test
        void Check_Image_URIs_From_Image_URI() throws ExecutionException, InterruptedException {
            URI imageUri = OmeroServer.getImageURI(OmeroServer.getRGBImage(userType));
            List<URI> expectedImageUris = List.of(imageUri);

            List<URI> imageUris = apisHandler.getImagesURIFromEntityURI(imageUri).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedImageUris, imageUris);
        }

        @Test
        void Check_Image_URIs_From_Web_Server_URI() {
            URI serverUri = URI.create(OmeroServer.getWebServerURI());

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.getImagesURIFromEntityURI(serverUri).get());
        }

        @Test
        void Check_Get_Image() throws ExecutionException, InterruptedException {
            URI imageLink = URI.create(OmeroServer.getWebServerURI() + "/static/webgateway/img/folder16.png");

            BufferedImage image = apisHandler.getImage(imageLink).get();

            Assertions.assertNotNull(image);
        }

        @Test
        void Check_Get_Image_On_Invalid_Link() {
            URI invalidImageLink = URI.create(OmeroServer.getWebServerURI());

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.getImage(invalidImageLink).get());
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
        void Check_Image_URI_Of_Invalid_Dataset() {
            long datasetID = -1;

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.getImagesURIOfDataset(datasetID).get());
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
        void Check_Image_URI_Of_Invalid_Project() {
            long projectID = -1;

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.getImagesURIOfProject(projectID).get());
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
        void Check_Ping() {
            Assertions.assertDoesNotThrow(() -> apisHandler.ping().get());
        }

        @Test
        void Check_Orphaned_Images_Id() throws ExecutionException, InterruptedException {
            List<Long> expectedIds = OmeroServer.getOrphanedImages(userType).stream().map(Image::getId).toList();

            List<Long> ids = apisHandler.getOrphanedImagesIds().get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedIds, ids);
        }

        @Test
        void Check_Groups_Of_User() throws ExecutionException, InterruptedException {
            long userId = OmeroServer.getConnectedOwner(userType).id();
            List<Group> expectedGroups = OmeroServer.getGroups(userType);

            List<Group> groups = apisHandler.getGroups(userId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedGroups, groups);
        }

        @Test
        void Check_Groups() throws ExecutionException, InterruptedException {
            List<Group> expectedGroups = OmeroServer.getGroups();

            List<Group> groups = apisHandler.getGroups().get();

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
        void Check_Datasets_Of_Invalid_Project() {
            long invalidProjectID = -1;

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.getDatasets(invalidProjectID).get());
        }

        @Test
        void Check_Parent_Dataset_Of_Image() throws ExecutionException, InterruptedException {
            Dataset expectedDataset = OmeroServer.getDatasets(userType).getLast();
            long imageId = OmeroServer.getImagesInDataset(expectedDataset).getFirst().getId();

            Dataset dataset = apisHandler.getDatasetOwningImage(imageId).get();

            Assertions.assertEquals(expectedDataset, dataset);
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
        void Check_Images_Of_Invalid_Dataset() {
            long invalidDatasetID = -1;

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.getImages(invalidDatasetID).get());
        }

        @Test
        void Check_Image() throws ExecutionException, InterruptedException {
            Image expectedImage = OmeroServer.getRGBImage(userType);
            long imageID = expectedImage.getId();

            Image image = apisHandler.getImage(imageID).get();

            Assertions.assertEquals(expectedImage, image);
        }

        @Test
        void Check_Image_With_Invalid_ID() {
            long imageID = -1;

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.getImage(imageID).get());
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
            ).get();

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
        abstract void Check_Key_Value_Pairs_Sent_When_Existing_Replaced_With_Same_Namespace() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Key_Value_Pairs_Sent_When_Existing_Not_Replaced_With_Same_Namespace() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Key_Value_Pairs_Sent_When_Existing_Replaced_With_Different_Namespace() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Key_Value_Pairs_Sent_When_Existing_Not_Replaced_With_Different_Namespace() throws ExecutionException, InterruptedException;

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

            BufferedImage icon = apisHandler.getOmeroIcon(type).get();

            Assertions.assertNotNull(icon);
        }

        @Test
        void Check_Image_Icon() throws ExecutionException, InterruptedException {
            Class<? extends RepositoryEntity> type = Image.class;

            BufferedImage icon = apisHandler.getOmeroIcon(type).get();

            Assertions.assertNotNull(icon);
        }

        @Test
        void Check_Orphaned_Folder_Icon() throws ExecutionException, InterruptedException {
            Class<? extends RepositoryEntity> type = OrphanedFolder.class;

            BufferedImage icon = apisHandler.getOmeroIcon(type).get();

            Assertions.assertNotNull(icon);
        }

        @Test
        void Check_Project_Icon() throws ExecutionException, InterruptedException {
            Class<? extends RepositoryEntity> type = Project.class;

            BufferedImage icon = apisHandler.getOmeroIcon(type).get();

            Assertions.assertNotNull(icon);
        }

        @Test
        void Check_Server_Icon() {
            Class<? extends RepositoryEntity> type = Server.class;

            Assertions.assertThrows(UncheckedExecutionException.class, () -> apisHandler.getOmeroIcon(type).get());
        }

        @Test
        void Check_Image_Thumbnail() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getRGBImage(userType).getId();

            BufferedImage image = apisHandler.getThumbnail(imageId).get();

            Assertions.assertNotNull(image);
        }

        @Test
        void Check_Image_Thumbnail_With_Specific_Size() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getRGBImage(userType).getId();
            int size = 30;

            BufferedImage image = apisHandler.getThumbnail(imageId, size).get();

            Assertions.assertEquals(size, Math.max(image.getWidth(), image.getHeight()));
        }

        @Test
        void Check_Image_Thumbnail_With_Invalid_Image_ID() {
            long invalidImageID = -1;

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.getThumbnail(invalidImageID).get());
        }

        @Test
        void Check_Image_Metadata() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getComplexImage(userType);
            long imageId = image.getId();
            ImageServerMetadata expectedMetadata = OmeroServer.getImageMetadata(image);

            ImageServerMetadata metadata = apisHandler.getImageMetadata(imageId).get();

            Assertions.assertNotNull(metadata);
            Assertions.assertEquals(expectedMetadata, metadata);
        }

        @Test
        void Check_Image_Metadata_With_Invalid_Image_ID() {
            long invalidImageID = -1;

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.getImageMetadata(invalidImageID).get());
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
        void Check_Get_Shapes_With_Invalid_Image_ID() throws ExecutionException, InterruptedException {
            long invalidImageID = -1;
            List<Shape> expectedShapes = List.of();

            List<Shape> shapes = apisHandler.getShapes(invalidImageID, -1).get();

            Assertions.assertEquals(expectedShapes, shapes);
        }

        @Test
        abstract void Check_Shapes_Deleted() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Shapes_Added() throws ExecutionException, InterruptedException;

        @Test
        void Check_Image_Settings() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getFloat32Image(userType);
            ImageSettings expectedImageSettings = OmeroServer.getFloat32ImageSettings();

            ImageSettings imageSettings = apisHandler.getImageSettings(image.getId()).get();

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
        static void createClient() {
            userType = UserType.UNAUTHENTICATED;
            client = OmeroServer.createClient(userType);
            apisHandler = client.getApisHandler();
        }

        @Test
        @Override
        void Check_Default_Group() {
            Optional<Group> defaultGroup = apisHandler.getDefaultGroup();

            Assertions.assertTrue(defaultGroup.isEmpty());
        }

        @Test
        @Override
        void Check_Session_Uuid() {
            Optional<String> sessionUuid = apisHandler.getSessionUuid();

            Assertions.assertTrue(sessionUuid.isEmpty());
        }

        @Test
        @Override
        void Check_Is_Admin() {
            Optional<Boolean> isAdmin = apisHandler.isAdmin();

            Assertions.assertTrue(isAdmin.isEmpty());
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent() {
            Image image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> keyValues = Map.of(
                    "A", "B",
                    "C", "D"
            );

            Assertions.assertThrows(ExecutionException.class, () ->
                    apisHandler.sendKeyValuePairs(image.getId(), new Namespace("qupath"), keyValues, true).get()
            );
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Replaced_With_Same_Namespace() {
            // Empty because key values can't be sent, see Check_Key_Value_Pairs_Sent
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Not_Replaced_With_Same_Namespace() {
            // Empty because key values can't be sent, see Check_Key_Value_Pairs_Sent
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Replaced_With_Different_Namespace() {
            // Empty because key values can't be sent, see Check_Key_Value_Pairs_Sent
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Not_Replaced_With_Different_Namespace() {
            // Empty because key values can't be sent, see Check_Key_Value_Pairs_Sent
        }

        @Test
        @Override
        void Check_Image_Name_Can_Be_Changed() {
            Image image = OmeroServer.getModifiableImage(userType);
            String newImageName = "new_name";

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.changeImageName(image.getId(), newImageName).get());
        }

        @Test
        @Override
        void Check_Image_Name_Changed() {
            // Empty because name can't be changed, see Check_Image_Name_Can_Be_Changed
        }

        @Test
        @Override
        void Check_Channel_Names_Can_Be_Changed() {
            Image image = OmeroServer.getModifiableImage(userType);
            List<String> newChannelsName = List.of("New channel 1", "New channel 2", "New channel 3");

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.changeChannelNames(image.getId(), newChannelsName).get());
        }

        @Test
        @Override
        void Check_Channel_Names_Changed() {
            // Empty because channel names can't be changed, see Check_Channels_Names_Can_Be_Changed
        }

        @Test
        @Override
        void Check_Channel_Colors_Can_Be_Changed() {
            Image image = OmeroServer.getModifiableImage(userType);
            List<Integer> channelColors = List.of(
                    Integer.parseInt("00FFFF", 16),
                    Integer.parseInt("FF00FF", 16),
                    Integer.parseInt("FFFF00", 16)
            );

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.changeChannelColors(image.getId(), channelColors).get());
        }

        @Test
        @Override
        void Check_Channel_Colors_Changed() {
            // Empty because channel colors can't be changed, see Check_Channel_Colors_Can_Be_Changed
        }

        @Test
        @Override
        void Check_Channel_Display_Ranges_Can_Be_Changed() {
            Image image = OmeroServer.getModifiableImage(userType);
            List<ChannelSettings> channelSettings = List.of(
                    new ChannelSettings(0.45, 100.654),
                    new ChannelSettings(50, 200),
                    new ChannelSettings(75.64, 80.9807)
            );

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.changeChannelDisplayRanges(image.getId(), channelSettings).get());
        }

        @Test
        @Override
        void Check_Channel_Display_Ranges_Changed() {
            // Empty because channel display ranges can't be changed, see Check_Channel_Display_Ranges_Can_Be_Changed
        }

        @Test
        @Override
        void Check_Shapes_Deleted() {
            long imageId = OmeroServer.getAnnotableImage(userType).getId();

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.deleteShapes(imageId, -1).get());
        }

        @Test
        @Override
        void Check_Shapes_Added() {
            long imageId = OmeroServer.getAnnotableImage(userType).getId();
            List<Shape> rois = List.of(new Rectangle(10, 10, 100, 100), new Line(20, 20, 50, 50));

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.addShapes(imageId, rois).get());
        }

        @Test
        @Override
        void Check_Attachments_Sent() {
            Image image = OmeroServer.getAnnotableImage(userType);

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.sendAttachment(
                    image.getId(),
                    image.getClass(),
                    "annotations.csv",
                    """
                    id,value
                    1,test1
                    2,test2
                    3,test3
                    """
            ).get());
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
        static void createClient() {
            userType = UserType.AUTHENTICATED;
            client = OmeroServer.createClient(userType);
            apisHandler = client.getApisHandler();
        }

        @Test
        @Override
        void Check_Default_Group() {
            Group expectedDefaultGroup = OmeroServer.getDefaultGroup(userType);

            Group defaultGroup = apisHandler.getDefaultGroup().orElse(null);

            Assertions.assertEquals(expectedDefaultGroup, defaultGroup);
        }

        @Test
        @Override
        void Check_Session_Uuid() {
            String sessionUuid = apisHandler.getSessionUuid().orElse(null);

            Assertions.assertNotNull(sessionUuid);
        }

        @Test
        @Override
        void Check_Is_Admin() {
            boolean isAdmin = apisHandler.isAdmin().orElseThrow();

            Assertions.assertFalse(isAdmin);
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent() {
            Image image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> keyValues = Map.of(
                    "A", "B"
            );

            Assertions.assertDoesNotThrow(() ->
                    apisHandler.sendKeyValuePairs(image.getId(), new Namespace("qupath"), keyValues, true).get()
            );
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Replaced_With_Same_Namespace() throws ExecutionException, InterruptedException {
            Namespace namespace = new Namespace(randomString());              // random so that it is not affected by other tests
            Image image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> existingKeyValues = Map.of(
                    "A", "existingValue"
            );
            apisHandler.sendKeyValuePairs(image.getId(), namespace, existingKeyValues, true).get();
            Map<String, String> keyValuesToSend = Map.of(
                    "A", "B",
                    "C", "D"
            );
            Map<String, String> expectedKeyValues = Map.of(
                    "A", "B",
                    "C", "D"
            );

            apisHandler.sendKeyValuePairs(image.getId(), namespace, keyValuesToSend, true).get();

            Assertions.assertEquals(
                    expectedKeyValues,
                    apisHandler.getAnnotations(image.getId(), Image.class).get()
                            .getAnnotationsOfClass(MapAnnotation.class)
                            .stream()
                            .filter(mapAnnotation -> mapAnnotation.getNamespace().isPresent() && mapAnnotation.getNamespace().get().equals(namespace))
                            .map(MapAnnotation::getPairs)
                            .flatMap(List::stream)
                            .collect(Collectors.toMap(
                                    MapAnnotation.Pair::key,
                                    MapAnnotation.Pair::value,
                                    (value1, value2) -> value1
                            ))
            );
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Not_Replaced_With_Same_Namespace() throws ExecutionException, InterruptedException {
            Namespace namespace = new Namespace(randomString());              // random so that it is not affected by other tests
            Image image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> existingKeyValues = Map.of(
                    "A", "existingValue"
            );
            apisHandler.sendKeyValuePairs(image.getId(), namespace, existingKeyValues, true).get();
            Map<String, String> keyValuesToSend = Map.of(
                    "A", "B",
                    "C", "D"
            );
            Map<String, String> expectedKeyValues = Map.of(
                    "A", "existingValue",
                    "C", "D"
            );

            apisHandler.sendKeyValuePairs(image.getId(), namespace, keyValuesToSend, false).get();

            Assertions.assertEquals(
                    expectedKeyValues,
                    apisHandler.getAnnotations(image.getId(), Image.class).get()
                            .getAnnotationsOfClass(MapAnnotation.class)
                            .stream()
                            .filter(mapAnnotation -> mapAnnotation.getNamespace().isPresent() && mapAnnotation.getNamespace().get().equals(namespace))
                            .map(MapAnnotation::getPairs)
                            .flatMap(List::stream)
                            .collect(Collectors.toMap(
                                    MapAnnotation.Pair::key,
                                    MapAnnotation.Pair::value,
                                    (value1, value2) -> value1
                            ))
            );
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Replaced_With_Different_Namespace() throws ExecutionException, InterruptedException {
            Namespace existingNamespace = new Namespace(randomString());          // random so that it is not affected by other tests
            Namespace differentNamespace = new Namespace(randomString());         // random so that it is not affected by other tests
            Image image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> existingKeyValues = Map.of(
                    "A", "existingValue"
            );
            apisHandler.sendKeyValuePairs(image.getId(), existingNamespace, existingKeyValues, true).get();
            Map<String, String> keyValuesToSend = Map.of(
                    "A", "B",
                    "C", "D"
            );
            Map<String, String> expectedKeyValues = Map.of(
                    "A", "B",
                    "C", "D"
            );

            apisHandler.sendKeyValuePairs(image.getId(), differentNamespace, keyValuesToSend, true).get();

            Assertions.assertEquals(
                    expectedKeyValues,
                    apisHandler.getAnnotations(image.getId(), Image.class).get()
                            .getAnnotationsOfClass(MapAnnotation.class)
                            .stream()
                            .filter(mapAnnotation -> mapAnnotation.getNamespace().isPresent() && mapAnnotation.getNamespace().get().equals(differentNamespace))
                            .map(MapAnnotation::getPairs)
                            .flatMap(List::stream)
                            .collect(Collectors.toMap(
                                    MapAnnotation.Pair::key,
                                    MapAnnotation.Pair::value,
                                    (value1, value2) -> value1
                            ))
            );
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Not_Replaced_With_Different_Namespace() throws ExecutionException, InterruptedException {
            Namespace existingNamespace = new Namespace(randomString());          // random so that it is not affected by other tests
            Namespace differentNamespace = new Namespace(randomString());         // random so that it is not affected by other tests
            Image image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> existingKeyValues = Map.of(
                    "A", "existingValue"
            );
            apisHandler.sendKeyValuePairs(image.getId(), existingNamespace, existingKeyValues, true).get();
            Map<String, String> keyValuesToSend = Map.of(
                    "A", "B",
                    "C", "D"
            );
            Map<String, String> expectedKeyValues = Map.of(
                    "A", "B",
                    "C", "D"
            );

            apisHandler.sendKeyValuePairs(image.getId(), differentNamespace, keyValuesToSend, false).get();

            Assertions.assertEquals(
                    expectedKeyValues,
                    apisHandler.getAnnotations(image.getId(), Image.class).get()
                            .getAnnotationsOfClass(MapAnnotation.class)
                            .stream()
                            .filter(mapAnnotation -> mapAnnotation.getNamespace().isPresent() && mapAnnotation.getNamespace().get().equals(differentNamespace))
                            .map(MapAnnotation::getPairs)
                            .flatMap(List::stream)
                            .collect(Collectors.toMap(
                                    MapAnnotation.Pair::key,
                                    MapAnnotation.Pair::value,
                                    (value1, value2) -> value1
                            ))
            );
        }

        @Test
        @Override
        void Check_Image_Name_Can_Be_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            String newImageName = "new_name";

            Assertions.assertDoesNotThrow(() -> apisHandler.changeImageName(image.getId(), newImageName).get());

            // Reset image name
            apisHandler.changeImageName(image.getId(), OmeroServer.getImageMetadata(image).getName()).get();
        }

        @Test
        @Override
        void Check_Image_Name_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            String expectedNewImageName = "new_name";

            apisHandler.changeImageName(image.getId(), expectedNewImageName).get();

            String newImageName = apisHandler.getImageSettings(image.getId()).get().getName();
            Assertions.assertEquals(expectedNewImageName, newImageName);

            // Reset image name
            apisHandler.changeImageName(image.getId(), OmeroServer.getImageMetadata(image).getName()).get();
        }

        @Test
        @Override
        void Check_Channel_Names_Can_Be_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<String> newChannelsName = List.of("New channel 1");

            Assertions.assertDoesNotThrow(() -> apisHandler.changeChannelNames(image.getId(), newChannelsName).get());

            // Reset channel names
            apisHandler.changeChannelNames(
                    image.getId(),
                    OmeroServer.getModifiableImageChannelSettings().stream().map(ChannelSettings::name).toList()
            ).get();
        }

        @Test
        @Override
        void Check_Channel_Names_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<String> expectedNewChannelNames = List.of("New channel 1");

            apisHandler.changeChannelNames(image.getId(), expectedNewChannelNames).get();

            List<String> newChannelNames = apisHandler.getImageSettings(image.getId()).get()
                    .getChannelSettings()
                    .stream()
                    .map(ChannelSettings::name)
                    .toList();
            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedNewChannelNames, newChannelNames);

            // Reset channel names
            apisHandler.changeChannelNames(
                    image.getId(),
                    OmeroServer.getModifiableImageChannelSettings().stream().map(ChannelSettings::name).toList()
            ).get();
        }

        @Test
        @Override
        void Check_Channel_Colors_Can_Be_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<Integer> channelColors = List.of(
                    Integer.parseInt("00FFFF", 16)
            );

            Assertions.assertDoesNotThrow(() -> apisHandler.changeChannelColors(image.getId(), channelColors).get());

            // Reset channel colors
            apisHandler.changeChannelColors(image.getId(), OmeroServer.getModifiableImageChannelSettings().stream()
                    .map(ChannelSettings::rgbColor)
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

            List<Integer> channelColors = apisHandler.getImageSettings(image.getId()).get()
                    .getChannelSettings()
                    .stream()
                    .map(ChannelSettings::rgbColor)
                    .toList();
            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChannelColors, channelColors);

            // Reset channel colors
            apisHandler.changeChannelColors(image.getId(), OmeroServer.getModifiableImageChannelSettings().stream()
                    .map(ChannelSettings::rgbColor)
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

            Assertions.assertDoesNotThrow(() -> apisHandler.changeChannelDisplayRanges(image.getId(), channelSettings).get());

            // Reset channel display ranges
            apisHandler.changeChannelDisplayRanges(image.getId(), OmeroServer.getModifiableImageChannelSettings()).get();
        }

        @Test
        @Override
        void Check_Channel_Display_Ranges_Changed() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getModifiableImage(userType);
            List<ChannelSettings> expectedChannelSettings = List.of(
                    new ChannelSettings(
                            OmeroServer.getModifiableImageChannelSettings().getFirst().name(),
                            0.45,
                            100.654,
                            OmeroServer.getModifiableImageChannelSettings().getFirst().rgbColor()
                    )
            );

            apisHandler.changeChannelDisplayRanges(image.getId(), expectedChannelSettings).get();

            List<ChannelSettings> channelSettings = apisHandler.getImageSettings(image.getId()).get().getChannelSettings();
            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChannelSettings, channelSettings);

            // Reset channel display ranges
            apisHandler.changeChannelDisplayRanges(image.getId(), OmeroServer.getModifiableImageChannelSettings()).get();
        }

        @Test
        @Override
        void Check_Shapes_Deleted() throws ExecutionException, InterruptedException {
            long userId = OmeroServer.getConnectedOwner(userType).id();
            long imageId = OmeroServer.getAnnotableImage(userType).getId();
            List<Shape> shapes = List.of(new Rectangle(10, 10, 100, 100), new Line(20, 20, 50, 50));
            apisHandler.addShapes(imageId, shapes).get();

            apisHandler.deleteShapes(imageId, userId).get();

            Assertions.assertTrue(apisHandler.getShapes(imageId, userId).get().isEmpty());
        }

        @Test
        @Override
        void Check_Shapes_Added() throws ExecutionException, InterruptedException {
            long userId = OmeroServer.getConnectedOwner(userType).id();
            long imageId = OmeroServer.getAnnotableImage(userType).getId();
            List<Shape> expectedShapes = List.of(new Rectangle(10, 10, 100, 100), new Line(20, 20, 50, 50));

            apisHandler.addShapes(imageId, expectedShapes).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedShapes, apisHandler.getShapes(imageId, userId).get());

            apisHandler.deleteShapes(imageId, userId).get();
        }

        @Test
        @Override
        void Check_Attachments_Sent() {
            Image image = OmeroServer.getAnnotableImage(userType);

            Assertions.assertDoesNotThrow(() -> apisHandler.sendAttachment(
                    image.getId(),
                    image.getClass(),
                    "annotations.csv",
                    """
                    id,value
                    1,test1
                    2,test2
                    3,test3
                    """
            ).get());
        }

        @Test
        @Override
        void Check_Existing_Attachments_Deleted() throws ExecutionException, InterruptedException {
            Image image = OmeroServer.getAnnotableImage(userType);
            apisHandler.sendAttachment(image.getId(), image.getClass(),"annotations1.csv", "test1").get();
            apisHandler.sendAttachment(image.getId(), image.getClass(),"annotations2.csv", "test2").get();
            apisHandler.sendAttachment(image.getId(), image.getClass(),"annotations3.csv", "test3").get();

            Assertions.assertDoesNotThrow(() -> apisHandler.deleteAttachments(image.getId(), image.getClass()).get());
        }

        private static String randomString() {
            int leftLimit = 48; // numeral '0'
            int rightLimit = 122; // letter 'z'
            int targetStringLength = 10;
            Random random = new Random();

            return random.ints(leftLimit, rightLimit + 1)
                    .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                    .limit(targetStringLength)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
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
        public String getLabel() {
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


