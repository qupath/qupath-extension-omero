package qupath.ext.omero.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class TestWebClients extends OmeroServer {

    abstract static class GenericWebClientCreation {

        protected abstract WebClient createClient(String url, WebClient.Authentication authentication, String... args) throws ExecutionException, InterruptedException;

        @Test
        void Check_Client_Creation_Without_Authentication() throws ExecutionException, InterruptedException {
            int numberOfAttempts = 3;       // This test might sometimes fail because of the responsiveness of the OMERO server
            WebClient.Status expectedStatus = WebClient.Status.SUCCESS;
            WebClient client;

            int attempt = 0;
            do {
                client = createClient(OmeroServer.getWebServerURI(), WebClient.Authentication.SKIP);
            } while (!client.getStatus().equals(expectedStatus) && ++attempt < numberOfAttempts);
            WebClient.Status status = client.getStatus();

            Assertions.assertEquals(expectedStatus, status);

            WebClients.removeClient(client);
        }

        @Test
        void Check_Client_Creation_With_Public_User() throws ExecutionException, InterruptedException {
            UserType userType = UserType.PUBLIC;
            WebClient client = createClient(
                    OmeroServer.getWebServerURI(),
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    OmeroServer.getUsername(userType),
                    "-p",
                    OmeroServer.getPassword(userType)
            );
            WebClient.Status expectedStatus = WebClient.Status.SUCCESS;

            WebClient.Status status = client.getStatus();

            Assertions.assertEquals(expectedStatus, status);

            WebClients.removeClient(client);
        }

        @Test
        void Check_Client_Creation_With_User() throws ExecutionException, InterruptedException {
            UserType userType = UserType.USER;
            WebClient client = createClient(
                    OmeroServer.getWebServerURI(),
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    OmeroServer.getUsername(userType),
                    "-p",
                    OmeroServer.getPassword(userType)
            );
            WebClient.Status expectedStatus = WebClient.Status.SUCCESS;

            WebClient.Status status = client.getStatus();

            Assertions.assertEquals(expectedStatus, status);

            WebClients.removeClient(client);
        }

        @Test
        void Check_Client_Creation_With_Incorrect_Username() throws ExecutionException, InterruptedException {
            UserType userType = UserType.USER;
            WebClient client = createClient(
                    OmeroServer.getWebServerURI(),
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    "incorrect_username",
                    "-p",
                    OmeroServer.getPassword(userType)
            );
            WebClient.Status expectedStatus = WebClient.Status.FAILED;

            WebClient.Status status = client.getStatus();

            Assertions.assertEquals(expectedStatus, status);

            WebClients.removeClient(client);
        }

        @Test
        void Check_Client_Creation_With_Incorrect_Password() throws ExecutionException, InterruptedException {
            UserType userType = UserType.USER;
            WebClient client = createClient(
                    OmeroServer.getWebServerURI(),
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    OmeroServer.getUsername(userType),
                    "-p",
                    "incorrect_password"
            );
            WebClient.Status expectedStatus = WebClient.Status.FAILED;

            WebClient.Status status = client.getStatus();

            Assertions.assertEquals(expectedStatus, status);

            WebClients.removeClient(client);
        }

        @Test
        void Check_Client_Creation_With_Invalid_URI() throws ExecutionException, InterruptedException {
            UserType userType = UserType.USER;
            WebClient client = createClient(
                    "",
                    WebClient.Authentication.ENFORCE,
                    "-u",
                    OmeroServer.getUsername(userType),
                    "-p",
                    OmeroServer.getPassword(userType)
            );
            WebClient.FailReason expectedFailReason = WebClient.FailReason.INVALID_URI_FORMAT;

            WebClient.FailReason failReason = client.getFailReason().orElse(null);

            Assertions.assertEquals(expectedFailReason, failReason);

            WebClients.removeClient(client);
        }

        @Test
        void Check_Client_Can_Be_Retrieved_After_Added() throws ExecutionException, InterruptedException {
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
        void Check_Client_Cannot_Be_Retrieved_After_Removed() throws ExecutionException, InterruptedException {
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
        void Check_Client_List_After_Added() throws ExecutionException, InterruptedException {
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
        void Check_Client_List_After_Removed() throws ExecutionException, InterruptedException {
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
        protected WebClient createClient(String url, WebClient.Authentication authentication, String... args) throws ExecutionException, InterruptedException {
            return WebClients.createClient(url, authentication, args).get();
        }
    }

    @Nested
    class SyncCreation extends GenericWebClientCreation {

        @Override
        protected WebClient createClient(String url, WebClient.Authentication authentication, String... args) {
            return WebClients.createClientSync(url, authentication, args);
        }
    }
}
