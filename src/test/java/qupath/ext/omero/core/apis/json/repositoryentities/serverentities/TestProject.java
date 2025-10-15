package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.Server;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class TestProject extends OmeroServer {

    abstract static class GenericUser {

        protected static UserType userType;
        protected static Client client;         // creating a client is necessary for the check children functions to work
        protected static Project project;

        @AfterAll
        static void closeClient() throws Exception {
            client.close();
        }

        @Test
        void Check_Has_Children() {
            boolean expectedChildren = !OmeroServer.getProjectDatasetIds(userType).isEmpty();

            boolean hasChildren = project.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            List<Long> expectedChildrenIds = OmeroServer.getProjectDatasetIds(userType);

            List<? extends RepositoryEntity> children = project.getChildren(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(Dataset.class::isInstance)
                            .map(Dataset.class::cast)
                            .map(Dataset::getId)
                            .toList()
            );
        }

        @Test
        void Check_Children_Filtered_By_Experimenter() throws InterruptedException, ExecutionException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            List<Long> expectedChildrenIds = OmeroServer.getProjectDatasetIds(userType);

            List<? extends RepositoryEntity> children = project.getChildren(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(Dataset.class::isInstance)
                            .map(Dataset.class::cast)
                            .map(Dataset::getId)
                            .toList()
            );
        }

        @Test
        void Check_Children_Filtered_By_Group() throws InterruptedException, ExecutionException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedChildrenIds = OmeroServer.getProjectDatasetIds(userType);

            List<? extends RepositoryEntity> children = project.getChildren(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(Dataset.class::isInstance)
                            .map(Dataset.class::cast)
                            .map(Dataset::getId)
                            .toList()
            );
        }

        @Test
        void Check_Children_Filtered_By_Experimenter_And_Group() throws InterruptedException, ExecutionException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedChildrenIds = OmeroServer.getProjectDatasetIds(userType);

            List<? extends RepositoryEntity> children = project.getChildren(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(Dataset.class::isInstance)
                            .map(Dataset.class::cast)
                            .map(Dataset::getId)
                            .toList()
            );
        }

        @Test
        void Check_Id() {
            long expectedId = OmeroServer.getProject(userType).id();

            long id = project.getId();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        void Check_Owner() {
            SimpleEntity expectedOwner = OmeroServer.getEntityOwner(userType);

            SimpleEntity owner = project.getOwner().orElseThrow();

            Assertions.assertEquals(expectedOwner, owner);
        }

        @Test
        void Check_Group() {
            SimpleEntity expectedGroup = OmeroServer.getEntityGroup(userType);

            SimpleEntity group = project.getGroup().orElseThrow();

            Assertions.assertEquals(expectedGroup, group);
        }

        @Test
        void Check_Name() {
            String expectedName = OmeroServer.getProjectName(userType);

            String name = project.getName().orElseThrow();

            Assertions.assertEquals(expectedName, name);
        }

        @Test
        void Check_Attributes_Values() {
            List<String> expectedAttributes = OmeroServer.getProjectAttributeValues(userType);

            List<Attribute> attributes = project.getAttributes();

            Assertions.assertEquals(
                    expectedAttributes,
                    attributes.stream().map(Attribute::value).toList()
            );
        }
    }

    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createProject() throws ExecutionException, InterruptedException {
            userType = UserType.AUTHENTICATED;
            client = OmeroServer.createClient(userType);
            Server server = client.getServer().get();

            project = server.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Project)
                    .map(p -> (Project) p)
                    .filter(p -> p.getId() == OmeroServer.getProject(userType).id())
                    .findAny()
                    .orElseThrow();
        }
    }

    @Nested
    class UnauthenticatedUser extends GenericUser {

        @BeforeAll
        static void createProject() throws ExecutionException, InterruptedException {
            userType = UserType.UNAUTHENTICATED;
            client = OmeroServer.createClient(userType);
            Server server = client.getServer().get();

            project = server.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Project)
                    .map(p -> (Project) p)
                    .filter(p -> p.getId() == OmeroServer.getProject(userType).id())
                    .findAny()
                    .orElseThrow();
        }
    }
}
