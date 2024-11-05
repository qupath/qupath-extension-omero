package qupath.ext.omero.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class TestWebClients extends OmeroServer {

    abstract static class GenericWebClientCreation {

        protected abstract WebClient createClient(String url, WebClient.Authentication authentication, String... args) throws Exception;

        @Test
        void Check_Client_Creation_Without_Authentication() {
            int numberOfAttempts = 3;       // This test might sometimes fail because of the responsiveness of the OMERO server
            WebClient client = null;

            int attempt = 0;
            do {
                try {
                    client = createClient(OmeroServer.getWebServerURI(), WebClient.Authentication.SKIP);
                } catch (Exception e) {
                    if (attempt == numberOfAttempts-1) {
                        throw new RuntimeException(e);
                    }
                }
            } while (client != null && ++attempt < numberOfAttempts);

            Assertions.assertNotNull(client);

            WebClients.removeClient(client);
        }

        @Test
        void Check_Client_Creation_With_Public_User() throws Exception {
            UserType userType = UserType.PUBLIC;

            WebClient client = createClient(
                    OmeroServer.getWebServerURI(),
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    OmeroServer.getUsername(userType),
                    "-p",
                    OmeroServer.getPassword(userType)
            );

            Assertions.assertNotNull(client);

            WebClients.removeClient(client);
        }

        @Test
        void Check_Client_Creation_With_User() throws Exception {
            UserType userType = UserType.USER;

            WebClient client = createClient(
                    OmeroServer.getWebServerURI(),
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    OmeroServer.getUsername(userType),
                    "-p",
                    OmeroServer.getPassword(userType)
            );

            Assertions.assertNotNull(client);

            WebClients.removeClient(client);
        }

        @Test
        void Check_Client_Creation_With_Incorrect_Username() {
            UserType userType = UserType.USER;

            Assertions.assertThrows(Exception.class, () -> createClient(
                    OmeroServer.getWebServerURI(),
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    "incorrect_username",
                    "-p",
                    OmeroServer.getPassword(userType)
            ));
        }

        @Test
        void Check_Client_Creation_With_Incorrect_Password() {
            UserType userType = UserType.USER;

            Assertions.assertThrows(Exception.class, () -> createClient(
                    OmeroServer.getWebServerURI(),
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    OmeroServer.getUsername(userType),
                    "-p",
                    "incorrect_password"
            ));
        }

        @Test
        void Check_Client_Creation_With_Invalid_URI() {
            UserType userType = UserType.USER;

            Assertions.assertThrows(Exception.class, () -> createClient(
                    "",
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    OmeroServer.getUsername(userType),
                    "-p",
                    OmeroServer.getPassword(userType)
            ));
        }

        @Test
        void Check_Client_Can_Be_Retrieved_After_Added() throws Exception {
            UserType userType = UserType.USER;
            URI uri = URI.create(OmeroServer.getWebServerURI());
            WebClient expectedClient = createClient(
                    uri.toString(),
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    OmeroServer.getUsername(userType),
                    "-p",
                    OmeroServer.getPassword(userType)
            );

            WebClient client = WebClients.getClientFromURI(uri).orElse(null);

            Assertions.assertEquals(expectedClient, client);

            WebClients.removeClient(expectedClient);
        }

        @Test
        void Check_Client_Cannot_Be_Retrieved_After_Removed() throws Exception {
            UserType userType = UserType.USER;
            URI uri = URI.create(OmeroServer.getWebServerURI());
            WebClient removedClient = createClient(
                    uri.toString(),
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    OmeroServer.getUsername(userType),
                    "-p",
                    OmeroServer.getPassword(userType)
            );

            WebClients.removeClient(removedClient);
            Optional<WebClient> client = WebClients.getClientFromURI(uri);

            Assertions.assertTrue(client.isEmpty());
        }

        @Test
        void Check_Client_List_After_Added() throws Exception {
            UserType userType = UserType.USER;
            WebClient client = createClient(
                    OmeroServer.getWebServerURI(),
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    OmeroServer.getUsername(userType),
                    "-p",
                    OmeroServer.getPassword(userType)
            );
            List<WebClient> expectedClients = List.of(client);

            List<WebClient> clients = WebClients.getClients();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedClients, clients);

            WebClients.removeClient(client);
        }

        @Test
        void Check_Client_List_After_Removed() throws Exception {
            UserType userType = UserType.USER;
            WebClient client = createClient(
                    OmeroServer.getWebServerURI(),
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    OmeroServer.getUsername(userType),
                    "-p",
                    OmeroServer.getPassword(userType)
            );
            List<WebClient> expectedClients = List.of();

            WebClients.removeClient(client);
            List<WebClient> clients = WebClients.getClients();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedClients, clients);
        }
    }

    @Nested
    class AsyncCreation extends GenericWebClientCreation {

        @Override
        protected WebClient createClient(String url, WebClient.Authentication authentication, String... args) throws Exception {
            return WebClients.createClient(url, authentication, args).get();
        }
    }

    @Nested
    class SyncCreation extends GenericWebClientCreation {

        @Override
        protected WebClient createClient(String url, WebClient.Authentication authentication, String... args) throws Exception {
            return WebClients.createClientSync(url, authentication, args);
        }
    }
}
