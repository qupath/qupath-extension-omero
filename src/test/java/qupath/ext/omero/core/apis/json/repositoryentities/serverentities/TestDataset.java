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

public class TestDataset extends OmeroServer {

    abstract static class GenericUser {

        protected static OmeroServer.UserType userType;
        protected static Client client;         // creating a client is necessary for the check children functions to work
        protected static Dataset dataset;

        @AfterAll
        static void closeClient() throws Exception {
            client.close();
        }

        @Test
        void Check_Has_Children() {
            boolean expectedChildren = !OmeroServer.getDatasetImageIds(userType, -1, -1).isEmpty();

            boolean hasChildren = dataset.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            List<Long> expectedChildrenIds = OmeroServer.getDatasetImageIds(userType, experimenterId, groupId);

            List<? extends RepositoryEntity> children = dataset.getChildren(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(Image.class::isInstance)
                            .map(Image.class::cast)
                            .map(Image::getId)
                            .toList()
            );
        }

        @Test
        void Check_Children_Filtered_By_Experimenter() throws InterruptedException, ExecutionException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            List<Long> expectedChildrenIds = OmeroServer.getDatasetImageIds(userType, experimenterId, groupId);

            List<? extends RepositoryEntity> children = dataset.getChildren(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(Image.class::isInstance)
                            .map(Image.class::cast)
                            .map(Image::getId)
                            .toList()
            );
        }

        @Test
        void Check_Children_Filtered_By_Group() throws InterruptedException, ExecutionException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedChildrenIds = OmeroServer.getDatasetImageIds(userType, experimenterId, groupId);

            List<? extends RepositoryEntity> children = dataset.getChildren(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(Image.class::isInstance)
                            .map(Image.class::cast)
                            .map(Image::getId)
                            .toList()
            );
        }

        @Test
        void Check_Children_Filtered_By_Experimenter_And_Group() throws InterruptedException, ExecutionException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedChildrenIds = OmeroServer.getDatasetImageIds(userType, experimenterId, groupId);

            List<? extends RepositoryEntity> children = dataset.getChildren(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(Image.class::isInstance)
                            .map(Image.class::cast)
                            .map(Image::getId)
                            .toList()
            );
        }

        @Test
        void Check_Id() {
            long expectedId = OmeroServer.getDataset(userType).id();

            long id = dataset.getId();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        void Check_Owner() {
            SimpleEntity expectedOwner = OmeroServer.getEntityOwner(userType);

            SimpleEntity owner = dataset.getOwner().orElseThrow();

            Assertions.assertEquals(expectedOwner, owner);
        }

        @Test
        void Check_Group() {
            SimpleEntity expectedGroup = OmeroServer.getEntityGroup(userType);

            SimpleEntity group = dataset.getGroup().orElseThrow();

            Assertions.assertEquals(expectedGroup, group);
        }

        @Test
        void Check_Name() {
            String expectedName = OmeroServer.getDatasetName(userType);

            String name = dataset.getName().orElseThrow();

            Assertions.assertEquals(expectedName, name);
        }

        @Test
        void Check_Attributes_Values() {
            List<String> expectedAttributes = OmeroServer.getDatasetAttributeValues(userType);

            List<Attribute> attributes = dataset.getAttributes();

            Assertions.assertEquals(
                    expectedAttributes,
                    attributes.stream().map(Attribute::value).toList()
            );
        }
    }

    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createDataset() throws ExecutionException, InterruptedException {
            userType = UserType.AUTHENTICATED;
            client = OmeroServer.createClient(userType);
            Server server = client.getServer().get();

            Project project = server.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Project)
                    .map(p -> (Project) p)
                    .filter(p -> p.getId() == OmeroServer.getProject(userType).id())
                    .findAny()
                    .orElseThrow();

            dataset = project.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Dataset)
                    .map(d -> (Dataset) d)
                    .filter(d -> d.getId() == OmeroServer.getDataset(userType).id())
                    .findAny()
                    .orElseThrow();
        }
    }

    @Nested
    class UnauthenticatedUser extends GenericUser {

        @BeforeAll
        static void createDataset() throws ExecutionException, InterruptedException {
            userType = UserType.UNAUTHENTICATED;
            client = OmeroServer.createClient(userType);
            Server server = client.getServer().get();

            Project project = server.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Project)
                    .map(p -> (Project) p)
                    .filter(p -> p.getId() == OmeroServer.getProject(userType).id())
                    .findAny()
                    .orElseThrow();

            dataset = project.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Dataset)
                    .map(d -> (Dataset) d)
                    .filter(d -> d.getId() == OmeroServer.getDataset(userType).id())
                    .findAny()
                    .orElseThrow();
        }
    }
}
