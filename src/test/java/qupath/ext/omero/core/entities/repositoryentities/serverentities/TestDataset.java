package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.Server;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class TestDataset extends OmeroServer {

    abstract static class GenericClient {

        protected static Credentials.UserType userType;
        protected static Client client;
        protected static Dataset dataset;

        @AfterAll
        static void removeClient() throws Exception {
            if (client != null) {
                client.close();
            }
        }

        @Test
        void Check_Has_Children() {
            boolean expectedChildren = !OmeroServer.getImagesInDataset(dataset).isEmpty();

            boolean hasChildren = dataset.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws InterruptedException {
            List<? extends RepositoryEntity> expectedChildren = OmeroServer.getImagesInDataset(dataset);

            List<? extends RepositoryEntity> children = dataset.getChildren();
            while (dataset.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
        }

        @Test
        void Check_Attributes() {
            int numberOfValues = dataset.getNumberOfAttributes();
            List<String> expectedAttributeValues = OmeroServer.getDatasetAttributeValue(dataset);

            List<String> attributesValues = IntStream.range(0, numberOfValues)
                    .mapToObj(i -> dataset.getAttributeValue(i))
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
            Project project = server.getChildren().stream()
                    .filter(child -> child instanceof Project)
                    .map(p -> (Project) p)
                    .findAny()
                    .orElse(null);
            assert project != null;

            List<? extends RepositoryEntity> projectChildren = project.getChildren();
            while (project.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }

            dataset = projectChildren.stream()
                    .filter(child -> child instanceof Dataset)
                    .map(d -> (Dataset) d)
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
            Project project = server.getChildren().stream()
                    .filter(child -> child instanceof Project)
                    .map(p -> (Project) p)
                    .findAny()
                    .orElse(null);
            assert project != null;

            List<? extends RepositoryEntity> projectChildren = project.getChildren();
            while (project.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }

            dataset = projectChildren.stream()
                    .filter(child -> child instanceof Dataset)
                    .map(d -> (Dataset) d)
                    .findAny()
                    .orElse(null);
        }
    }
}
