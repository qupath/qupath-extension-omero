package qupath.ext.omero.core;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.core.pixelapis.web.WebApi;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class TestClient extends OmeroServer {

    abstract static class GenericClient {

        protected static Client client;

        @AfterAll
        static void removeClient() throws Exception {
            if (client != null) {
                client.close();
            }
        }

        @Test
        void Check_Selected_Pixel_API() {
            PixelApi expectedPixelApi = client.getPixelAPI(WebApi.class);
            client.setSelectedPixelAPI(expectedPixelApi);

            PixelApi pixelAPI = client.getSelectedPixelApi().get();

            Assertions.assertEquals(expectedPixelApi, pixelAPI);
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
        static void createClient() {
            client = OmeroServer.createClient(Credentials.UserType.PUBLIC_USER);
        }
    }

    @Nested
    class AuthenticatedClient extends GenericClient {

        @BeforeAll
        static void createClient() {
            client = OmeroServer.createClient(Credentials.UserType.REGULAR_USER);
        }
    }

    @Test
    void Check_Client_Creation_Without_Authentication() throws Exception {
        int numberOfAttempts = 3;       // This test might sometimes fail because of the responsiveness of the OMERO server
        Client client = null;

        int attempt = 0;
        do {
            try {
                client = Client.createOrGet(OmeroServer.getWebServerURI(), new Credentials());
            } catch (Exception e) {
                if (attempt == numberOfAttempts-1) {
                    throw new RuntimeException(e);
                }
            }
        } while (client != null && ++attempt < numberOfAttempts);

        Assertions.assertNotNull(client);

        client.close();
    }

    @Test
    void Check_Client_Creation_With_Public_User() {
        Credentials.UserType userType = Credentials.UserType.PUBLIC_USER;

        Assertions.assertDoesNotThrow(() -> {
            Client client = Client.createOrGet(
                    OmeroServer.getWebServerURI(),
                    new Credentials(OmeroServer.getUsername(userType), OmeroServer.getPassword(userType).toCharArray())
            );
            client.close();
        });
    }

    @Test
    void Check_Client_Creation_With_User() {
        Credentials.UserType userType = Credentials.UserType.REGULAR_USER;

        Assertions.assertDoesNotThrow(() -> {
            Client client = Client.createOrGet(
                    OmeroServer.getWebServerURI(),
                    new Credentials(OmeroServer.getUsername(userType), OmeroServer.getPassword(userType).toCharArray())
            );
            client.close();
        });
    }

    @Test
    void Check_Client_Creation_With_Incorrect_Username() {
        Credentials.UserType userType = Credentials.UserType.REGULAR_USER;

        Assertions.assertThrows(Exception.class, () -> {
            try (Client client = Client.createOrGet(
                    OmeroServer.getWebServerURI(),
                    new Credentials("incorrect_username", OmeroServer.getPassword(userType).toCharArray())
            )) {}
        });
    }

    @Test
    void Check_Client_Creation_With_Incorrect_Password() {
        Credentials.UserType userType = Credentials.UserType.REGULAR_USER;

        Assertions.assertThrows(Exception.class, () -> {
            try (Client client = Client.createOrGet(
                    OmeroServer.getWebServerURI(),
                    new Credentials(OmeroServer.getUsername(userType), "incorrect_password".toCharArray())
            )) {}
        });
    }

    @Test
    void Check_Client_Creation_With_Invalid_URI() {
        Credentials.UserType userType = Credentials.UserType.REGULAR_USER;

        Assertions.assertThrows(Exception.class, () -> {
            try (Client client = Client.createOrGet(
                    "invalid_uri",
                    new Credentials(OmeroServer.getUsername(userType), OmeroServer.getPassword(userType).toCharArray())
            )) {}
        });
    }

    @Test
    void Check_Client_Can_Be_Retrieved_After_Added() throws Exception {
        Credentials.UserType userType = Credentials.UserType.REGULAR_USER;
        URI uri = URI.create(OmeroServer.getWebServerURI());
        Client expectedClient = Client.createOrGet(
                uri.toString(),
                new Credentials(OmeroServer.getUsername(userType), OmeroServer.getPassword(userType).toCharArray())
        );

        Client client = Client.getClientFromURI(uri).orElse(null);

        Assertions.assertEquals(expectedClient, client);

        expectedClient.close();
    }

    @Test
    void Check_Client_Cannot_Be_Retrieved_After_Closed() throws Exception {
        Credentials.UserType userType = Credentials.UserType.REGULAR_USER;
        URI uri = URI.create(OmeroServer.getWebServerURI());
        Client removedClient = Client.createOrGet(
                uri.toString(),
                new Credentials(OmeroServer.getUsername(userType), OmeroServer.getPassword(userType).toCharArray())
        );

        removedClient.close();
        Optional<Client> client = Client.getClientFromURI(uri);

        Assertions.assertTrue(client.isEmpty());
    }

    @Test
    void Check_Client_List_After_Added() throws Exception {
        Credentials.UserType userType = Credentials.UserType.REGULAR_USER;
        Client client = Client.createOrGet(
                OmeroServer.getWebServerURI(),
                new Credentials(OmeroServer.getUsername(userType), OmeroServer.getPassword(userType).toCharArray())
        );
        List<Client> expectedClients = List.of(client);

        List<Client> clients = Client.getClients();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedClients, clients);

        client.close();
    }

    @Test
    void Check_Client_List_After_Closed() throws Exception {
        Credentials.UserType userType = Credentials.UserType.REGULAR_USER;
        Client client = Client.createOrGet(
                OmeroServer.getWebServerURI(),
                new Credentials(OmeroServer.getUsername(userType), OmeroServer.getPassword(userType).toCharArray())
        );
        List<Client> expectedClients = List.of();

        client.close();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedClients, Client.getClients());
    }

    @Test
    void Check_Existing_Client_Retrieved() throws Exception {
        Credentials.UserType userType = Credentials.UserType.REGULAR_USER;
        Client expectedClient = Client.createOrGet(
                OmeroServer.getWebServerURI(),
                new Credentials(OmeroServer.getUsername(userType), OmeroServer.getPassword(userType).toCharArray())
        );

        Client client = Client.createOrGet(
                OmeroServer.getWebServerURI(),
                new Credentials(OmeroServer.getUsername(userType), OmeroServer.getPassword(userType).toCharArray())
        );

        Assertions.assertEquals(expectedClient, client);

        client.close();
    }

    @Test
    void Check_Existing_Authenticated_Client_Disconnected() throws Exception {
        Client.createOrGet(
                OmeroServer.getWebServerURI(),
                new Credentials(
                        OmeroServer.getUsername(Credentials.UserType.REGULAR_USER),
                        OmeroServer.getPassword(Credentials.UserType.REGULAR_USER).toCharArray()
                )
        );

        Client client = Client.createOrGet(
                OmeroServer.getWebServerURI(),
                new Credentials(
                        OmeroServer.getUsername(Credentials.UserType.PUBLIC_USER),
                        OmeroServer.getPassword(Credentials.UserType.PUBLIC_USER).toCharArray()
                )
        );

        TestUtilities.assertCollectionsEqualsWithoutOrder(List.of(client), Client.getClients());

        client.close();
    }

    @Test
    void Check_Existing_Unauthenticated_Client_Disconnected() throws Exception {
        Client.createOrGet(
                OmeroServer.getWebServerURI(),
                new Credentials()
        );

        Client client = Client.createOrGet(
                OmeroServer.getWebServerURI(),
                new Credentials(
                        OmeroServer.getUsername(Credentials.UserType.PUBLIC_USER),
                        OmeroServer.getPassword(Credentials.UserType.PUBLIC_USER).toCharArray()
                )
        );

        TestUtilities.assertCollectionsEqualsWithoutOrder(List.of(client), Client.getClients());

        client.close();
    }
}
