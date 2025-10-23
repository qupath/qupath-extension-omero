package qupath.ext.omero.core.apis.iviewer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.commonentities.shapes.Ellipse;
import qupath.ext.omero.core.apis.iviewer.imageentities.ImageData;
import qupath.ext.omero.core.apis.json.JsonApi;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroEllipse;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class TestIViewerApi extends OmeroServer {

    abstract static class GenericUser {

        protected static OmeroServer.UserType userType;
        protected static RequestSender requestSender;
        protected static JsonApi jsonApi;
        protected static IViewerApi iViewerApi;

        @AfterAll
        static void closeApis() throws Exception {
            iViewerApi.close();
            jsonApi.close();
            requestSender.close();
        }

        @Test
        void Check_Shapes_Cannot_Be_Added_With_Invalid_Image_Id() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> iViewerApi.addShapes(invalidId, List.of(new Ellipse(
                            new OmeroEllipse(
                                    1L,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    0d,
                                    0d,
                                    1d,
                                    1d,
                                    null
                            ),
                            1
                    ))).get()
            );
        }

        @Test
        void Check_Shapes_Cannot_Be_Deleted_With_Invalid_Image_Id() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> iViewerApi.deleteShapes(invalidId, List.of()).get()
            );
        }

        @Test
        void Check_Image_Data() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getFloat32Image(userType).id();
            ImageData expectedImageData = OmeroServer.getFloat32ImageData();

            ImageData imageData = iViewerApi.getImageData(imageId).get();

            Assertions.assertEquals(expectedImageData, imageData);
        }

        @Test
        void Check_Image_Settings_Cannot_Be_Retrieved_With_Invalid_Image_Id() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> iViewerApi.getImageData(invalidId).get()
            );
        }
    }

    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createClient() throws URISyntaxException, ExecutionException, InterruptedException {
            userType = UserType.AUTHENTICATED;
            requestSender = new RequestSender();
            jsonApi = new JsonApi(URI.create(OmeroServer.getWebServerURI()), requestSender, OmeroServer.getCredentials(userType));
            iViewerApi = new IViewerApi(URI.create(OmeroServer.getWebServerURI()), requestSender, jsonApi.getToken());
        }
    }

    @Nested
    class UnauthenticatedUser extends GenericUser {

        @BeforeAll
        static void createClient() throws URISyntaxException, ExecutionException, InterruptedException {
            userType = UserType.UNAUTHENTICATED;
            requestSender = new RequestSender();
            jsonApi = new JsonApi(URI.create(OmeroServer.getWebServerURI()), requestSender, OmeroServer.getCredentials(userType));
            iViewerApi = new IViewerApi(URI.create(OmeroServer.getWebServerURI()), requestSender, jsonApi.getToken());
        }
    }
}
