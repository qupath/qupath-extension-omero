package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;

import java.util.List;
import java.util.concurrent.ExecutionException;

//TODO: other entities
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
            boolean expectedChildren = !OmeroServer.getImageIdsInDataset(dataset).isEmpty();

            boolean hasChildren = dataset.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            List<? extends RepositoryEntity> expectedChildren = OmeroServer.getImageIdsInDataset(dataset);

            List<? extends RepositoryEntity> children = dataset.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
        }

        @Test
        void Check_Children_Filtered_By_Experimenter() throws InterruptedException, ExecutionException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            List<? extends RepositoryEntity> expectedChildren = OmeroServer.getImageIdsInDataset(dataset);

            List<? extends RepositoryEntity> children = dataset.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
        }

        @Test
        void Check_Children_Filtered_By_Group() throws InterruptedException, ExecutionException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<? extends RepositoryEntity> expectedChildren = OmeroServer.getImageIdsInDataset(dataset);

            List<? extends RepositoryEntity> children = dataset.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
        }

        @Test
        void Check_Children_Filtered_By_Experimenter_And_Group() throws InterruptedException, ExecutionException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<? extends RepositoryEntity> expectedChildren = OmeroServer.getImageIdsInDataset(dataset);

            List<? extends RepositoryEntity> children = dataset.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
        }

        @Test
        void Check_Id() {
            long expectedId = OmeroServer.getDatasetId(userType);

            long id = dataset.getId();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        void Check_Owner() {
            SimpleEntity expectedOwner = OmeroServer.getOwnerOfEntity(dataset);

            SimpleEntity owner = dataset.getOwner().orElseThrow();

            Assertions.assertEquals(expectedOwner, owner);
        }

        @Test
        void Check_Group() {
            SimpleEntity expectedGroup = OmeroServer.getGroupOfEntity(dataset);

            SimpleEntity group = dataset.getGroup().orElseThrow();

            Assertions.assertEquals(expectedGroup, group);
        }

        @Test
        void Check_Name() {
            String expectedName = OmeroServer.getNameOfEntity(dataset);

            String name = dataset.getName().orElseThrow();

            Assertions.assertEquals(expectedName, name);
        }

        @Test
        void Check_Attributes() {
            List<Attribute> expectedAttributes = OmeroServer.getAttributes(dataset);

            List<Attribute> attributes = dataset.getAttributes();

            Assertions.assertEquals(expectedAttributes, attributes);
        }
    }

    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createDataset() {
            userType = UserType.AUTHENTICATED;
            client = OmeroServer.createClient(userType);
            dataset = OmeroServer.getDataset(userType);
        }
    }

    @Nested
    class UnauthenticatedUser extends GenericUser {

        @BeforeAll
        static void createDataset() {
            userType = UserType.UNAUTHENTICATED;
            client = OmeroServer.createClient(userType);
            dataset = OmeroServer.getDataset(userType);
        }
    }
}
