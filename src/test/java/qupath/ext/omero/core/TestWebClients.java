package qupath.ext.omero.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class TestWebClients extends OmeroServer {

    @Test
    void Check_Client_Creation_Without_Authentication() {
        int numberOfAttempts = 3;       // This test might sometimes fail because of the responsiveness of the OMERO server
        WebClient client = null;

        int attempt = 0;
        do {
            try {
                client = WebClients.createClient(OmeroServer.getWebServerURI(), WebClient.Authentication.SKIP).get();
            } catch (ExecutionException | InterruptedException e) {
                if (attempt == numberOfAttempts-1) {
                    throw new RuntimeException(e);
                }
            }
        } while (client != null && ++attempt < numberOfAttempts);

        Assertions.assertNotNull(client);

        WebClients.removeClient(client);
    }

    @Test
    void Check_Client_Creation_With_Public_User() throws ExecutionException, InterruptedException {
        UserType userType = UserType.PUBLIC;

        WebClient client = WebClients.createClient(
                OmeroServer.getWebServerURI(),
                WebClient.Authentication.ENFORCE,
                "-u",
                OmeroServer.getUsername(userType),
                "-p",
                OmeroServer.getPassword(userType)
        ).get();

        Assertions.assertNotNull(client);

        WebClients.removeClient(client);
    }

    @Test
    void Check_Client_Creation_With_User() throws ExecutionException, InterruptedException {
        UserType userType = UserType.USER;

        WebClient client = WebClients.createClient(
                OmeroServer.getWebServerURI(),
                WebClient.Authentication.ENFORCE,
                "-u",
                OmeroServer.getUsername(userType),
                "-p",
                OmeroServer.getPassword(userType)
        ).get();

        Assertions.assertNotNull(client);

        WebClients.removeClient(client);
    }

    @Test
    void Check_Client_Creation_With_Incorrect_Username() {
        UserType userType = UserType.USER;

        Assertions.assertThrows(ExecutionException.class, () -> WebClients.createClient(
                OmeroServer.getWebServerURI(),
                WebClient.Authentication.ENFORCE,
                "-u",
                "incorrect_username",
                "-p",
                OmeroServer.getPassword(userType)
        ).get());
    }

    @Test
    void Check_Client_Creation_With_Incorrect_Password() {
        UserType userType = UserType.USER;

        Assertions.assertThrows(ExecutionException.class, () -> WebClients.createClient(
                OmeroServer.getWebServerURI(),
                WebClient.Authentication.ENFORCE,
                "-u",
                OmeroServer.getUsername(userType),
                "-p",
                "incorrect_password"
        ).get());
    }

    @Test
    void Check_Client_Creation_With_Invalid_URI() {
        UserType userType = UserType.USER;

        Assertions.assertThrows(IllegalArgumentException.class, () -> WebClients.createClient(
                "",
                WebClient.Authentication.ENFORCE,
                "-u",
                OmeroServer.getUsername(userType),
                "-p",
                OmeroServer.getPassword(userType)
        ).get());
    }

    @Test
    void Check_Client_Can_Be_Retrieved_After_Added() throws ExecutionException, InterruptedException {
        UserType userType = UserType.USER;
        URI uri = URI.create(OmeroServer.getWebServerURI());
        WebClient expectedClient = WebClients.createClient(
                uri.toString(),
                WebClient.Authentication.ENFORCE,
                "-u",
                OmeroServer.getUsername(userType),
                "-p",
                OmeroServer.getPassword(userType)
        ).get();

        WebClient client = WebClients.getClientFromURI(uri).orElse(null);

        Assertions.assertEquals(expectedClient, client);

        WebClients.removeClient(expectedClient);
    }

    @Test
    void Check_Client_Cannot_Be_Retrieved_After_Removed() throws ExecutionException, InterruptedException {
        UserType userType = UserType.USER;
        URI uri = URI.create(OmeroServer.getWebServerURI());
        WebClient removedClient = WebClients.createClient(
                uri.toString(),
                WebClient.Authentication.ENFORCE,
                "-u",
                OmeroServer.getUsername(userType),
                "-p",
                OmeroServer.getPassword(userType)
        ).get();

        WebClients.removeClient(removedClient);
        Optional<WebClient> client = WebClients.getClientFromURI(uri);

        Assertions.assertTrue(client.isEmpty());
    }

    @Test
    void Check_Client_List_After_Added() throws ExecutionException, InterruptedException {
        UserType userType = UserType.USER;
        WebClient client = WebClients.createClient(
                OmeroServer.getWebServerURI(),
                WebClient.Authentication.ENFORCE,
                "-u",
                OmeroServer.getUsername(userType),
                "-p",
                OmeroServer.getPassword(userType)
        ).get();
        List<WebClient> expectedClients = List.of(client);

        List<WebClient> clients = WebClients.getClients();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedClients, clients);

        WebClients.removeClient(client);
    }

    @Test
    void Check_Client_List_After_Removed() throws ExecutionException, InterruptedException {
        UserType userType = UserType.USER;
        WebClient client = WebClients.createClient(
                OmeroServer.getWebServerURI(),
                WebClient.Authentication.ENFORCE,
                "-u",
                OmeroServer.getUsername(userType),
                "-p",
                OmeroServer.getPassword(userType)
        ).get();
        List<WebClient> expectedClients = List.of();

        WebClients.removeClient(client);
        List<WebClient> clients = WebClients.getClients();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedClients, clients);
    }
}
