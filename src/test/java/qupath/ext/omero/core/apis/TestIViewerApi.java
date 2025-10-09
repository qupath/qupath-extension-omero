package qupath.ext.omero.core.apis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.commonentities.image.ImageSettings;
import qupath.ext.omero.core.apis.json.JsonApi;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

public class TestIViewerApi extends OmeroServer {

    abstract static class GenericUser {

        protected static IViewerApi iViewerApi;
        protected static RequestSender requestSender;
        protected static OmeroServer.UserType userType;

        @AfterAll
        static void closeRequestSender() throws Exception {
            requestSender.close();
        }

        @Test
        void Check_Image_Settings() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getFloat32Image(userType).id();
            ImageSettings expectedImageSettings = OmeroServer.getFloat32ImageSettings();

            ImageSettings imageSettings = iViewerApi.getImageSettings(imageId).get();

            Assertions.assertEquals(expectedImageSettings, imageSettings);
        }
    }

    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createClient() throws URISyntaxException, ExecutionException, InterruptedException {
            userType = UserType.AUTHENTICATED;
            requestSender = new RequestSender();
            JsonApi jsonApi = new JsonApi(URI.create(OmeroServer.getWebServerURI()), requestSender, OmeroServer.getCredentials(userType));
            iViewerApi = new IViewerApi(URI.create(OmeroServer.getWebServerURI()), requestSender, jsonApi.getToken());
        }
    }

    @Nested
    class UnauthenticatedUser extends GenericUser {

        @BeforeAll
        static void createClient() throws URISyntaxException, ExecutionException, InterruptedException {
            userType = UserType.UNAUTHENTICATED;
            requestSender = new RequestSender();
            JsonApi jsonApi = new JsonApi(URI.create(OmeroServer.getWebServerURI()), requestSender, OmeroServer.getCredentials(userType));
            iViewerApi = new IViewerApi(URI.create(OmeroServer.getWebServerURI()), requestSender, jsonApi.getToken());
        }
    }
}
