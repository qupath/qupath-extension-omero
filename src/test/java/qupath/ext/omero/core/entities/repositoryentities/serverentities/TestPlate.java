package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class TestPlate extends OmeroServer {

    abstract static class GenericClient {

        protected static UserType userType;
        protected static WebClient client;
        protected static Plate plate;

        @AfterAll
        static void removeClient() {
            WebClients.removeClient(client);
        }

        @Test
        void Check_Has_Children() {
            boolean expectedChildren = !OmeroServer.getImagesInPlate(plate).isEmpty();

            boolean hasChildren = plate.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws InterruptedException {
            List<? extends RepositoryEntity> expectedChildren = OmeroServer.getImagesInPlate(plate);

            List<? extends RepositoryEntity> children = plate.getChildren();
            while (plate.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
        }

        @Test
        void Check_Attributes() {
            int numberOfValues = plate.getNumberOfAttributes();
            List<String> expectedAttributeValues = OmeroServer.getPlateAttributeValue(plate);

            List<String> attributesValues = IntStream.range(0, numberOfValues)
                    .mapToObj(i -> plate.getAttributeValue(i))
                    .toList();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedAttributeValues, attributesValues);
        }
    }

    @Nested
    class UnauthenticatedClient extends GenericClient {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            userType = UserType.PUBLIC;
            client = OmeroServer.createClient(userType);

            while (client.getServer().isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }
            Screen screen = client.getServer().getChildren().stream()
                    .filter(child -> child instanceof Screen)
                    .map(s -> (Screen) s)
                    .findAny()
                    .orElse(null);
            assert screen != null;

            List<? extends RepositoryEntity> projectChildren = screen.getChildren();
            while (screen.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }

            plate = projectChildren.stream()
                    .filter(child -> child instanceof Plate)
                    .map(p -> (Plate) p)
                    .findAny()
                    .orElse(null);
        }
    }

    @Nested
    class AuthenticatedClient extends GenericClient {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            userType = UserType.USER;
            client = OmeroServer.createClient(userType);

            while (client.getServer().isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }
            Screen screen = client.getServer().getChildren().stream()
                    .filter(child -> child instanceof Screen)
                    .map(s -> (Screen) s)
                    .findAny()
                    .orElse(null);
            assert screen != null;

            List<? extends RepositoryEntity> projectChildren = screen.getChildren();
            while (screen.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }

            plate = projectChildren.stream()
                    .filter(child -> child instanceof Plate)
                    .map(p -> (Plate) p)
                    .findAny()
                    .orElse(null);
        }
    }
}
