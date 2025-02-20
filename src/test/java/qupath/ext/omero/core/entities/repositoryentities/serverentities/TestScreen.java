package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.Server;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class TestScreen extends OmeroServer {

    abstract static class GenericClient {

        protected static Credentials.UserType userType;
        protected static Client client;
        protected static Screen screen;

        @AfterAll
        static void removeClient() throws Exception {
            if (client != null) {
                client.close();
            }
        }

        @Test
        void Check_Has_Children() {
            boolean expectedChildren = !OmeroServer.getPlatesInScreen(screen).isEmpty();

            boolean hasChildren = screen.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws InterruptedException {
            List<? extends RepositoryEntity> expectedChildren = OmeroServer.getPlatesInScreen(screen);

            List<? extends RepositoryEntity> children = screen.getChildren();
            while (screen.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
        }

        @Test
        void Check_Attributes() {
            int numberOfValues = screen.getNumberOfAttributes();
            List<String> expectedAttributeValues = OmeroServer.getScreenAttributeValue(screen);

            List<String> attributesValues = IntStream.range(0, numberOfValues)
                    .mapToObj(i -> screen.getAttributeValue(i))
                    .toList();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedAttributeValues, attributesValues);
        }
    }

    @Nested
    class UnauthenticatedClient extends GenericClient {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            userType = Credentials.UserType.PUBLIC_USER;
            client = OmeroServer.createClient(userType);
            Server server = client.getServer().get();

            while (server.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }
            screen = server.getChildren().stream()
                    .filter(child -> child instanceof Screen)
                    .map(s -> (Screen) s)
                    .findAny()
                    .orElse(null);
        }
    }

    @Nested
    class AuthenticatedClient extends GenericClient {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            userType = Credentials.UserType.REGULAR_USER;
            client = OmeroServer.createClient(userType);
            Server server = client.getServer().get();

            while (server.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }
            screen = server.getChildren().stream()
                    .filter(child -> child instanceof Screen)
                    .map(s -> (Screen) s)
                    .findAny()
                    .orElse(null);
        }
    }
}
