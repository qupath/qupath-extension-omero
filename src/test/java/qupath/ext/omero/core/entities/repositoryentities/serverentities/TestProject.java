package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.Server;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class TestProject extends OmeroServer {

    abstract static class GenericClient {

        protected static UserType userType;
        protected static Client client;
        protected static Project project;

        @AfterAll
        static void removeClient() throws Exception {
            if (client != null) {
                client.close();
            }
        }

        @Test
        void Check_Owner() {
            Owner expectedOwner = OmeroServer.getOwnerOfEntity(project);

            Owner owner = project.getOwner();

            Assertions.assertEquals(expectedOwner, owner);
        }

        @Test
        void Check_Group_Id() {
            long expectedGroupId = OmeroServer.getGroupOfEntity(project).getId();

            long groupId = project.getGroupId();

            Assertions.assertEquals(expectedGroupId, groupId);
        }

        @Test
        void Check_Group_Name() {
            String expectedGroupName = OmeroServer.getGroupOfEntity(project).getName();

            String groupName = project.getGroupName();

            Assertions.assertEquals(expectedGroupName, groupName);
        }

        @Test
        void Check_Has_Children() {
            boolean expectedChildren = !OmeroServer.getDatasets(userType).isEmpty();

            boolean hasChildren = project.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws InterruptedException {
            List<? extends RepositoryEntity> expectedChildren = OmeroServer.getDatasets(userType);

            List<? extends RepositoryEntity> children = project.getChildren();
            while (project.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
        }

        @Test
        void Check_Attributes() {
            int numberOfValues = project.getNumberOfAttributes();
            List<String> expectedAttributeValues = OmeroServer.getProjectAttributeValue(project);

            List<String> attributesValues = IntStream.range(0, numberOfValues)
                    .mapToObj(i -> project.getAttributeValue(i))
                    .toList();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedAttributeValues, attributesValues);
        }
    }

    @Nested
    class UnauthenticatedClient extends GenericClient {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            userType = UserType.UNAUTHENTICATED;
            client = OmeroServer.createClient(userType);
            Server server = client.getServer().get();

            while (server.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }
            project = server.getChildren().stream()
                    .filter(child -> child instanceof Project)
                    .map(project -> (Project) project)
                    .findAny()
                    .orElse(null);
        }
    }

    @Nested
    class AuthenticatedClient extends GenericClient {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            userType = UserType.AUTHENTICATED;
            client = OmeroServer.createClient(userType);
            Server server = client.getServer().get();

            while (server.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }
            project = server.getChildren().stream()
                    .filter(child -> child instanceof Project)
                    .map(project -> (Project) project)
                    .findAny()
                    .orElse(null);
        }
    }
}
