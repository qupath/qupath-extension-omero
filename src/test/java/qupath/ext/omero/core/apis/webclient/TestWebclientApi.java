package qupath.ext.omero.core.apis.webclient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.json.JsonApi;
import qupath.ext.omero.core.apis.json.permissions.Experimenter;
import qupath.ext.omero.core.apis.json.permissions.ExperimenterGroup;
import qupath.ext.omero.core.apis.webclient.annotations.Annotation;
import qupath.ext.omero.core.apis.webclient.annotations.MapAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.Pair;
import qupath.ext.omero.core.apis.webclient.search.SearchQuery;
import qupath.ext.omero.core.apis.webclient.search.SearchResult;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;

public class TestWebclientApi extends OmeroServer {

    abstract static class GenericUser {

        protected static WebclientApi webclientApi;
        protected static RequestSender requestSender;
        protected static OmeroServer.UserType userType;

        @AfterAll
        static void closeApi() throws Exception {
            webclientApi.close();
            requestSender.close();
        }

        @Test
        void Check_Image_Uri() {
            SimpleServerEntity image = OmeroServer.getRGBImage(userType);
            String expectedUri = OmeroServer.getImageUri(image.id()).toString();

            String uri = webclientApi.getEntityUri(image);

            Assertions.assertEquals(expectedUri, uri);
        }

        @Test
        void Check_Dataset_Uri() {
            SimpleServerEntity dataset = OmeroServer.getDataset(userType);
            String expectedUri = OmeroServer.getDatasetUri(dataset.id()).toString();

            String uri = webclientApi.getEntityUri(dataset);

            Assertions.assertEquals(expectedUri, uri);
        }

        @Test
        void Check_Project_Uri() {
            SimpleServerEntity project = OmeroServer.getProject(userType);
            String expectedUri = OmeroServer.getProjectUri(project.id()).toString();

            String uri = webclientApi.getEntityUri(project);

            Assertions.assertEquals(expectedUri, uri);
        }

        @Test
        void Check_Screen_Uri() {
            SimpleServerEntity screen = OmeroServer.getSimpleScreen(userType);
            String expectedUri = OmeroServer.getScreenUri(screen.id()).toString();

            String uri = webclientApi.getEntityUri(screen);

            Assertions.assertEquals(expectedUri, uri);
        }

        @Test
        void Check_Plate_Uri() {
            SimpleServerEntity plate = OmeroServer.getSimplePlate(userType);
            String expectedUri = OmeroServer.getPlateUri(plate.id()).toString();

            String uri = webclientApi.getEntityUri(plate);

            Assertions.assertEquals(expectedUri, uri);
        }

        @Test
        void Check_Plate_Acquisition_Uri() {
            SimpleServerEntity plateAcquisition = OmeroServer.getSimplePlateAcquisition(userType);
            String expectedURI = OmeroServer.getPlateAcquisitionUri(plateAcquisition.id()).toString();

            String uri = webclientApi.getEntityUri(plateAcquisition);

            Assertions.assertEquals(expectedURI, uri);
        }

        @Test
        void Check_Well_Uri() {
            SimpleServerEntity well = OmeroServer.getSimpleWell(userType);
            String expectedUri = OmeroServer.getWellUri(well.id()).toString();

            String uri = webclientApi.getEntityUri(well);

            Assertions.assertEquals(expectedUri, uri);
        }

        @Test
        void Check_Ping() {
            Assertions.assertDoesNotThrow(() -> webclientApi.ping().get());
        }

        @Test
        abstract void Check_Public_User_Id() throws ExecutionException, InterruptedException;

        @Test
        void Check_Parents_Of_Image() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getRGBImage(userType).id();
            List<SimpleServerEntity> expectedParents = OmeroServer.getParentsOfImage(imageId).stream()
                    .map(SimpleServerEntity::new)
                    .toList();

            List<SimpleServerEntity> parents = webclientApi.getParentsOfImage(imageId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedParents, parents);
        }

        @Test
        void Check_Annotations() throws ExecutionException, InterruptedException {
            SimpleServerEntity dataset = OmeroServer.getDataset(userType);
            List<Annotation> expectedAnnotations = OmeroServer.getAnnotationsInDataset(dataset.id());

            List<Annotation> annotations = webclientApi.getAnnotations(dataset).get();

            Assertions.assertEquals(expectedAnnotations, annotations);
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
                    Experimenter.getAllExperimenters().getId(),
                    ExperimenterGroup.getAllGroups().getId()
            );
            List<SearchResult> expectedResults = OmeroServer.getSearchResultsOnDataset(userType);

            List<SearchResult> results = webclientApi.getSearchResults(searchQuery).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedResults, results);
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
        abstract void Check_Channel_Names_Can_Be_Changed() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Attachments_Sent() throws ExecutionException, InterruptedException;

        @Test
        abstract void Check_Existing_Attachments_Deleted() throws ExecutionException, InterruptedException;

        @Test
        void Check_Image_Icon() throws ExecutionException, InterruptedException {
            Assertions.assertNotNull(webclientApi.getImageIcon().get());
        }

        @Test
        void Check_Screen_Icon() throws ExecutionException, InterruptedException {
            Assertions.assertNotNull(webclientApi.getScreenIcon().get());
        }

        @Test
        void Check_Plate_Icon() throws ExecutionException, InterruptedException {
            Assertions.assertNotNull(webclientApi.getPlateIcon().get());
        }

        @Test
        void Check_Plate_Acquisition_Icon() throws ExecutionException, InterruptedException {
            Assertions.assertNotNull(webclientApi.getPlateAcquisitionIcon().get());
        }
    }

    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createClient() throws URISyntaxException, ExecutionException, InterruptedException {
            userType = UserType.AUTHENTICATED;
            requestSender = new RequestSender();
            JsonApi jsonApi = new JsonApi(URI.create(OmeroServer.getWebServerURI()),requestSender, OmeroServer.getCredentials(userType));
            webclientApi = new WebclientApi(URI.create(OmeroServer.getWebServerURI()), requestSender, jsonApi.getToken());
        }

        @Test
        @Override
        void Check_Public_User_Id() {
            // getPublicUserId() only works if there's no authenticated connection to the server
            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> webclientApi.getPublicUserId().get()
            );
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent() {
            SimpleServerEntity image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> keyValues = Map.of(
                    "A", "B"
            );

            Assertions.assertDoesNotThrow(() ->
                    webclientApi.sendKeyValuePairs(image.id(), new Namespace("qupath"), keyValues, true).get()
            );
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Replaced_With_Same_Namespace() throws ExecutionException, InterruptedException {
            Namespace namespace = new Namespace(randomString());              // random so that it is not affected by other tests
            SimpleServerEntity image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> existingPairs = Map.of(
                    "A", "existingValue"
            );
            webclientApi.sendKeyValuePairs(image.id(), namespace, existingPairs, true).get();
            Map<String, String> pairsToSend = Map.of(
                    "A", "B",
                    "C", "D"
            );
            List<Pair> expectedPairs = List.of(
                    new Pair("A", "B"),
                    new Pair("C", "D")
            );

            webclientApi.sendKeyValuePairs(image.id(), namespace, pairsToSend, true).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
                    expectedPairs,
                    webclientApi.getAnnotations(image).get()
                            .stream()
                            .filter(MapAnnotation.class::isInstance)
                            .map(MapAnnotation.class::cast)
                            .filter(mapAnnotation -> mapAnnotation.getNamespace().isPresent() && mapAnnotation.getNamespace().get().equals(namespace))
                            .map(MapAnnotation::getPairs)
                            .flatMap(List::stream)
                            .toList()
            );
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Not_Replaced_With_Same_Namespace() throws ExecutionException, InterruptedException {
            Namespace namespace = new Namespace(randomString());              // random so that it is not affected by other tests
            SimpleServerEntity image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> existingPairs = Map.of(
                    "A", "existingValue"
            );
            webclientApi.sendKeyValuePairs(image.id(), namespace, existingPairs, true).get();
            Map<String, String> pairsToSend = Map.of(
                    "A", "B",
                    "C", "D"
            );
            List<Pair> expectedPairs = List.of(
                    new Pair("A", "existingValue"),
                    new Pair("C", "D")
            );

            webclientApi.sendKeyValuePairs(image.id(), namespace, pairsToSend, false).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
                    expectedPairs,
                    webclientApi.getAnnotations(image).get()
                            .stream()
                            .filter(MapAnnotation.class::isInstance)
                            .map(MapAnnotation.class::cast)
                            .filter(mapAnnotation -> mapAnnotation.getNamespace().isPresent() && mapAnnotation.getNamespace().get().equals(namespace))
                            .map(MapAnnotation::getPairs)
                            .flatMap(List::stream)
                            .toList()
            );
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Replaced_With_Different_Namespace() throws ExecutionException, InterruptedException {
            Namespace existingNamespace = new Namespace(randomString());          // random so that it is not affected by other tests
            Namespace differentNamespace = new Namespace(randomString());         // random so that it is not affected by other tests
            SimpleServerEntity image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> existingPairs = Map.of(
                    "A", "existingValue"
            );
            webclientApi.sendKeyValuePairs(image.id(), existingNamespace, existingPairs, true).get();
            Map<String, String> pairsToSend = Map.of(
                    "A", "B",
                    "C", "D"
            );
            List<Pair> expectedPairs = List.of(
                    new Pair("A", "B"),
                    new Pair("C", "D")
            );

            webclientApi.sendKeyValuePairs(image.id(), differentNamespace, pairsToSend, true).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
                    expectedPairs,
                    webclientApi.getAnnotations(image).get()
                            .stream()
                            .filter(MapAnnotation.class::isInstance)
                            .map(MapAnnotation.class::cast)
                            .filter(mapAnnotation -> mapAnnotation.getNamespace().isPresent() && mapAnnotation.getNamespace().get().equals(differentNamespace))
                            .map(MapAnnotation::getPairs)
                            .flatMap(List::stream)
                            .toList()
            );
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent_When_Existing_Not_Replaced_With_Different_Namespace() throws ExecutionException, InterruptedException {
            Namespace existingNamespace = new Namespace(randomString());          // random so that it is not affected by other tests
            Namespace differentNamespace = new Namespace(randomString());         // random so that it is not affected by other tests
            SimpleServerEntity image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> existingPairs = Map.of(
                    "A", "existingValue"
            );
            webclientApi.sendKeyValuePairs(image.id(), existingNamespace, existingPairs, true).get();
            Map<String, String> pairsToSend = Map.of(
                    "A", "B",
                    "C", "D"
            );
            List<Pair> expectedPairs = List.of(
                    new Pair("A", "B"),
                    new Pair("C", "D")
            );

            webclientApi.sendKeyValuePairs(image.id(), differentNamespace, pairsToSend, false).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
                    expectedPairs,
                    webclientApi.getAnnotations(image).get()
                            .stream()
                            .filter(MapAnnotation.class::isInstance)
                            .map(MapAnnotation.class::cast)
                            .filter(mapAnnotation -> mapAnnotation.getNamespace().isPresent() && mapAnnotation.getNamespace().get().equals(differentNamespace))
                            .map(MapAnnotation::getPairs)
                            .flatMap(List::stream)
                            .toList()
            );
        }

        @Test
        @Override
        void Check_Image_Name_Can_Be_Changed() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getModifiableImage(userType).id();
            String newImageName = "new_name";

            Assertions.assertDoesNotThrow(() -> webclientApi.changeImageName(imageId, newImageName).get());

            // Reset image name
            webclientApi.changeImageName(imageId, OmeroServer.getImageMetadata(imageId).getName()).get();
        }

        @Test
        @Override
        void Check_Channel_Names_Can_Be_Changed() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getModifiableImage(userType).id();
            List<String> newChannelsName = List.of("New channel 1");

            Assertions.assertDoesNotThrow(() -> webclientApi.changeChannelNames(imageId, newChannelsName).get());

            // Reset channel names
            webclientApi.changeChannelNames(
                    imageId,
                    List.of(OmeroServer.getModifiableImageChannelSettings().name())
            ).get();
        }

        @Test
        @Override
        void Check_Attachments_Sent() {
            SimpleServerEntity image = OmeroServer.getAnnotableImage(userType);

            Assertions.assertDoesNotThrow(() -> webclientApi.sendAttachment(
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
            webclientApi.sendAttachment(image,"annotations1.csv", "test1").get();
            webclientApi.sendAttachment(image,"annotations2.csv", "test2").get();
            webclientApi.sendAttachment(image,"annotations3.csv", "test3").get();

            Assertions.assertDoesNotThrow(() -> webclientApi.deleteAttachments(image, List.of(ownerFullName)).get());
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

    @Nested
    class UnauthenticatedUser extends GenericUser {

        @BeforeAll
        static void createClient() throws URISyntaxException, ExecutionException, InterruptedException {
            userType = UserType.UNAUTHENTICATED;
            requestSender = new RequestSender();
            JsonApi jsonApi = new JsonApi(URI.create(OmeroServer.getWebServerURI()),requestSender, OmeroServer.getCredentials(userType));
            webclientApi = new WebclientApi(URI.create(OmeroServer.getWebServerURI()), requestSender, jsonApi.getToken());
        }

        @Test
        @Override
        void Check_Public_User_Id() throws ExecutionException, InterruptedException {
            long expectedPublicUserId = OmeroServer.getConnectedExperimenter(userType).getId();

            long publicUserId = webclientApi.getPublicUserId().get();

            Assertions.assertEquals(expectedPublicUserId, publicUserId);
        }

        @Test
        @Override
        void Check_Key_Value_Pairs_Sent() {
            SimpleServerEntity image = OmeroServer.getAnnotableImage(userType);
            Map<String, String> keyValues = Map.of(
                    "A", "B",
                    "C", "D"
            );

            Assertions.assertThrows(
                    ExecutionException.class, () ->
                    webclientApi.sendKeyValuePairs(image.id(), new Namespace("qupath"), keyValues, true).get()
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
            SimpleServerEntity image = OmeroServer.getModifiableImage(userType);
            String newImageName = "new_name";

            Assertions.assertThrows(ExecutionException.class, () -> webclientApi.changeImageName(image.id(), newImageName).get());
        }

        @Test
        @Override
        void Check_Channel_Names_Can_Be_Changed() {
            SimpleServerEntity image = OmeroServer.getModifiableImage(userType);
            List<String> newChannelsName = List.of("New channel 1", "New channel 2", "New channel 3");

            Assertions.assertThrows(ExecutionException.class, () -> webclientApi.changeChannelNames(image.id(), newChannelsName).get());
        }

        @Test
        @Override
        void Check_Attachments_Sent() {
            SimpleServerEntity image = OmeroServer.getAnnotableImage(userType);

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> webclientApi.sendAttachment(
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
}
