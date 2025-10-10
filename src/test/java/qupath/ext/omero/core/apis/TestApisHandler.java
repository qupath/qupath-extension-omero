package qupath.ext.omero.core.apis;

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
import qupath.ext.omero.core.apis.json.permissions.Experimenter;
import qupath.ext.omero.core.apis.json.permissions.ExperimenterGroup;
import qupath.ext.omero.core.apis.commonentities.image.ChannelSettings;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.apis.webclient.SimpleServerEntity;
import qupath.ext.omero.core.apis.webclient.search.SearchQuery;
import qupath.ext.omero.core.apis.webclient.search.SearchResultWithParentInfo;
import qupath.ext.omero.core.apis.commonentities.shapes.Line;
import qupath.ext.omero.core.apis.commonentities.shapes.Rectangle;
import qupath.ext.omero.core.apis.commonentities.shapes.Shape;
import qupath.lib.images.servers.PixelType;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

//TODO: tests with invalid image IDs (e.g. -1) in other APIs
public class TestApisHandler extends OmeroServer {

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
        void Check_Web_Server_Uri() {
            URI expectedWebServerUri = URI.create(OmeroServer.getWebServerURI());

            URI webServerUri = apisHandler.getWebServerUri();

            Assertions.assertEquals(expectedWebServerUri, webServerUri);
        }

        @Test
        void Check_Credentials() {
            Credentials expectedCredentials = OmeroServer.getCredentials(userType);

            Credentials credentials = apisHandler.getCredentials();

            Assertions.assertEquals(expectedCredentials, credentials);
        }

        @Test
        void Check_Parents_Of_Image() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getRGBImage(userType).id();
            List<? extends ServerEntity> expectedParents = OmeroServer.getParentsOfImage(imageId);

            List<? extends ServerEntity> parents = apisHandler.getParentsOfImage(imageId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedParents, parents);
        }

        @Test
        void Check_Base_Url_Reachable() {
            URI serverURI = URI.create(OmeroServer.getWebServerURI());

            Assertions.assertDoesNotThrow(() -> apisHandler.isLinkReachable(serverURI, RequestSender.RequestType.GET).get());
        }

        @Test
        void Check_Image_Uris_From_Dataset_Uri() throws ExecutionException, InterruptedException {
            long datasetId = OmeroServer.getDataset(userType).id();
            URI datasetUri = OmeroServer.getDatasetUri(datasetId);
            List<URI> expectedImageUris = OmeroServer.getImageIdsInDataset(datasetId).stream()
                    .map(OmeroServer::getImageUri)
                    .toList();

            List<URI> imageUris = apisHandler.getImageUrisFromEntityURI(datasetUri).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedImageUris, imageUris);
        }

        @Test
        void Check_Image_Uris_From_Project_Uri() throws ExecutionException, InterruptedException {
            long projectId = OmeroServer.getProject(userType).id();
            URI projectUri = OmeroServer.getProjectUri(projectId);
            List<URI> expectedImageUris = OmeroServer.getProjectDatasetIds(userType).stream()
                    .map(OmeroServer::getImageIdsInDataset)
                    .flatMap(List::stream)
                    .map(OmeroServer::getImageUri)
                    .toList();

            List<URI> imageUris = apisHandler.getImageUrisFromEntityURI(projectUri).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedImageUris, imageUris);
        }

        @Test
        void Check_Image_Uris_From_Image_Uri() throws ExecutionException, InterruptedException {
            URI imageUri = OmeroServer.getImageUri(OmeroServer.getRGBImage(userType).id());
            List<URI> expectedImageUris = List.of(imageUri);

            List<URI> imageUris = apisHandler.getImageUrisFromEntityURI(imageUri).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedImageUris, imageUris);
        }

        @Test
        void Check_Image_Uris_From_Web_Server_Uri() {
            URI serverUri = URI.create(OmeroServer.getWebServerURI());

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.getImageUrisFromEntityURI(serverUri).get());
        }

        @Test
        void Check_Search() throws ExecutionException, InterruptedException {
            SearchQuery searchQuery = new SearchQuery(
                    "image",
                    false,
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    ExperimenterGroup.getAllGroups().getId(),
                    Experimenter.getAllExperimenters().getId()
            );
            List<SearchResultWithParentInfo> expectedResults = OmeroServer.getSearchResultsOnImage(userType);

            List<SearchResultWithParentInfo> searchResults = apisHandler.getSearchResults(searchQuery).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedResults, searchResults);
        }

        @Test
        abstract void Check_Image_Name_Changed() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Channel_Names_Changed() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Channel_Colors_Changed() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Channel_Display_Ranges_Changed() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Shapes_Added() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Shapes_Deleted() throws ExecutionException, InterruptedException;

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
        void Check_Image_Name_Changed() {
            // Empty because name can't be changed, see Check_Image_Name_Can_Be_Changed of TestWebClientApi
        }

        @Test
        @Override
        void Check_Channel_Names_Changed() {
            // Empty because channel names can't be changed, see Check_Channels_Names_Can_Be_Changed of TestWebClientApi
        }

        @Test
        @Override
        void Check_Channel_Colors_Changed() {
            // Empty because channel colors can't be changed, see Check_Channel_Colors_Can_Be_Changed of TestWebGatewayApi
        }

        @Test
        @Override
        void Check_Channel_Display_Ranges_Changed() {
            // Empty because channel display ranges can't be changed, see Check_Channel_Display_Ranges_Can_Be_Changed of TestWebGatewayApi
        }

        @Test
        @Override
        void Check_Shapes_Added() {
            long imageId = OmeroServer.getAnnotableImage(userType).id();

            List<Shape> rois = List.of(
                    new Rectangle(
                            PathObjects.createAnnotationObject(ROIs.createRectangleROI(10, 10, 100, 100, ImagePlane.getDefaultPlane())),
                            false
                    ),
                    new Line(
                            PathObjects.createAnnotationObject(ROIs.createLineROI(20, 20, 50, 50, ImagePlane.getDefaultPlane())),
                            false
                    )
            );

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.addShapes(imageId, rois).get());
        }

        @Test
        @Override
        void Check_Shapes_Deleted() {
            long imageId = OmeroServer.getAnnotableImage(userType).id();

            Assertions.assertThrows(ExecutionException.class, () -> apisHandler.deleteShapes(imageId, List.of()).get());
        }

        @Test
        @Override
        void Check_Attachments_Sent() {
            SimpleServerEntity image = OmeroServer.getAnnotableImage(userType);

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> apisHandler.sendAttachment(
                            image,
                            "annotations.csv",
                    """
                            id,value
                            1,test1
                            2,test2
                            3,test3
                            """
                    ).get()
            );
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
        void Check_Image_Name_Changed() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getModifiableImage(userType).id();
            String expectedNewImageName = "new_name";

            apisHandler.changeImageName(imageId, expectedNewImageName).get();

            String newImageName = apisHandler.getImageSettings(imageId).get().getName();
            Assertions.assertEquals(expectedNewImageName, newImageName);

            // Reset image name
            apisHandler.changeImageName(imageId, OmeroServer.getImageMetadata(imageId).getName()).get();
        }

        @Test
        @Override
        void Check_Channel_Names_Changed() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getModifiableImage(userType).id();
            List<String> expectedNewChannelNames = List.of("New channel 1");

            apisHandler.changeChannelNames(imageId, expectedNewChannelNames).get();

            List<String> newChannelNames = apisHandler.getImageSettings(imageId).get()
                    .getChannelSettings()
                    .stream()
                    .map(ChannelSettings::name)
                    .toList();
            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedNewChannelNames, newChannelNames);

            // Reset channel names
            apisHandler.changeChannelNames(
                    imageId,
                    List.of(OmeroServer.getModifiableImageChannelSettings().name())
            ).get();
        }

        @Test
        @Override
        void Check_Channel_Colors_Changed() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getModifiableImage(userType).id();
            List<Integer> expectedChannelColors = List.of(
                    Integer.parseInt("00FFFF", 16)
            );

            apisHandler.changeChannelColors(imageId, expectedChannelColors).get();

            List<Integer> channelColors = apisHandler.getImageSettings(imageId).get()
                    .getChannelSettings()
                    .stream()
                    .map(ChannelSettings::rgbColor)
                    .toList();
            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChannelColors, channelColors);

            // Reset channel colors
            apisHandler.changeChannelColors(
                    imageId,
                    List.of(OmeroServer.getModifiableImageChannelSettings().rgbColor())
            ).get();
        }

        @Test
        @Override
        void Check_Channel_Display_Ranges_Changed() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getModifiableImage(userType).id();
            List<ChannelSettings> expectedChannelSettings = List.of(
                    new ChannelSettings(
                            OmeroServer.getModifiableImageChannelSettings().name(),
                            0.45,
                            100.654,
                            OmeroServer.getModifiableImageChannelSettings().rgbColor()
                    )
            );

            apisHandler.changeChannelDisplayRanges(imageId, expectedChannelSettings).get();

            List<ChannelSettings> channelSettings = apisHandler.getImageSettings(imageId).get().getChannelSettings();
            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChannelSettings, channelSettings);

            // Reset channel display ranges
            apisHandler.changeChannelDisplayRanges(imageId, List.of(OmeroServer.getModifiableImageChannelSettings())).get();
        }

        @Test
        @Override
        void Check_Shapes_Added() throws ExecutionException, InterruptedException {
            long userId = OmeroServer.getConnectedExperimenter(userType).getId();
            long imageId = OmeroServer.getAnnotableImage(userType).id();
            List<Shape> expectedShapes = List.of(
                    new Rectangle(
                            PathObjects.createAnnotationObject(ROIs.createRectangleROI(10, 10, 100, 100, ImagePlane.getDefaultPlane())),
                            false
                    ),
                    new Line(
                            PathObjects.createAnnotationObject(ROIs.createLineROI(20, 20, 50, 50, ImagePlane.getDefaultPlane())),
                            false
                    )
            );

            apisHandler.addShapes(imageId, expectedShapes).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedShapes, apisHandler.getShapes(imageId, userId).get());

            apisHandler.deleteShapes(imageId, List.of(userId)).get();
        }

        @Test
        @Override
        void Check_Shapes_Deleted() throws ExecutionException, InterruptedException {
            long userId = OmeroServer.getConnectedExperimenter(userType).getId();
            long imageId = OmeroServer.getAnnotableImage(userType).id();
            List<Shape> shapes = List.of(
                    new Rectangle(
                            PathObjects.createAnnotationObject(ROIs.createRectangleROI(10, 10, 100, 100, ImagePlane.getDefaultPlane())),
                            false
                    ),
                    new Line(
                            PathObjects.createAnnotationObject(ROIs.createLineROI(20, 20, 50, 50, ImagePlane.getDefaultPlane())),
                            false
                    )
            );
            apisHandler.addShapes(imageId, shapes).get();

            apisHandler.deleteShapes(imageId, List.of(userId)).get();

            Assertions.assertTrue(apisHandler.getShapes(imageId, userId).get().isEmpty());
        }

        @Test
        @Override
        void Check_Attachments_Sent() {
            SimpleServerEntity image = OmeroServer.getAnnotableImage(userType);

            Assertions.assertDoesNotThrow(() -> apisHandler.sendAttachment(
                    image,
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
            String ownerFullName = OmeroServer.getConnectedExperimenter(userType).getFullName();
            SimpleServerEntity image = OmeroServer.getAnnotableImage(userType);
            apisHandler.sendAttachment(image,"annotations1.csv", "test1").get();
            apisHandler.sendAttachment(image,"annotations2.csv", "test2").get();
            apisHandler.sendAttachment(image,"annotations3.csv", "test3").get();

            Assertions.assertDoesNotThrow(() -> apisHandler.deleteAttachments(image, List.of(ownerFullName)).get());
        }
    }
}
