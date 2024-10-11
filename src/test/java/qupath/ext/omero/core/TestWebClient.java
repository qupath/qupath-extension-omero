package qupath.ext.omero.core;

import org.junit.jupiter.api.*;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.pixelapis.PixelAPI;
import qupath.ext.omero.core.pixelapis.web.WebAPI;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class TestWebClient extends OmeroServer {

    abstract static class GenericClient {

        protected static WebClient client;
        protected static UserType userType;

        @AfterAll
        static void removeClient() {
            WebClients.removeClient(client);
        }

        @Test
        abstract void Check_Client_Authentication();

        @Test
        void Check_Client_Username() {
            String expectedUsername = OmeroServer.getUsername(userType);

            String username = client.getUsername();

            Assertions.assertEquals(expectedUsername, username);
        }

        @Test
        void Check_Selected_Pixel_API() {
            PixelAPI expectedPixelAPI = client.getPixelAPI(WebAPI.class);
            client.setSelectedPixelAPI(expectedPixelAPI);

            PixelAPI pixelAPI = client.getSelectedPixelAPI().get();

            Assertions.assertEquals(expectedPixelAPI, pixelAPI);
        }

        @Test
        void Check_Opened_Images_When_One_Image_Added() {
            int expectedSize = client.getOpenedImagesURIs().size() + 1;

            client.addOpenedImage(URI.create(OmeroServer.getWebServerURI()));
            Set<URI> openedImagesURIs = client.getOpenedImagesURIs();

            Assertions.assertEquals(expectedSize, openedImagesURIs.size());
        }

        @Test
        void Check_Client_Can_Be_Closed() {
            boolean canBeClosed = client.canBeClosed();

            Assertions.assertTrue(canBeClosed);
        }
    }

    @Nested
    class UnauthenticatedClient extends GenericClient {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            userType = UserType.PUBLIC;
            client = OmeroServer.createClient(userType);
        }

        @Test
        @Override
        void Check_Client_Authentication() {
            boolean isAuthenticated = client.isAuthenticated();

            Assertions.assertFalse(isAuthenticated);
        }
    }

    @Nested
    class AuthenticatedClient extends GenericClient {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            userType = UserType.USER;
            client = OmeroServer.createClient(userType);
        }

        @Test
        @Override
        void Check_Client_Authentication() {
            boolean isAuthenticated = client.isAuthenticated();

            Assertions.assertTrue(isAuthenticated);
        }
    }
}
