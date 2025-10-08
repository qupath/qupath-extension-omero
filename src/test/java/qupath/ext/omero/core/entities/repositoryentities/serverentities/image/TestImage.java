package qupath.ext.omero.core.entities.repositoryentities.serverentities.image;

import org.junit.jupiter.api.*;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class TestImage extends OmeroServer {

    abstract static class GenericImage {

        protected static final UserType userType = UserType.UNAUTHENTICATED;
        protected static Client client;
        protected static Image image;

        @AfterAll
        static void removeClient() throws Exception {
            if (client != null) {
                client.close();
            }
        }

        @Test
        void Check_Id() {
            long expectedId = getImageId();

            long id = image.getId();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        void Check_Owner() {
            Owner expectedOwner = OmeroServer.getOwnerOfEntity(image);

            Owner owner = image.getOwner();

            Assertions.assertEquals(expectedOwner, owner);
        }

        @Test
        void Check_Group_Id() {
            long expectedGroupId = OmeroServer.getGroupOfEntity(image).getId();

            long groupId = image.getGroupId();

            Assertions.assertEquals(expectedGroupId, groupId);
        }

        @Test
        void Check_Group_Name() {
            String expectedGroupName = OmeroServer.getGroupOfEntity(image).getName();

            String groupName = image.getGroupName();

            Assertions.assertEquals(expectedGroupName, groupName);
        }

        @Test
        void Check_Has_Children() {
            boolean expectedChildren = false;

            boolean hasChildren = image.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws InterruptedException {
            List<? extends RepositoryEntity> expectedChildren = List.of();

            List<? extends RepositoryEntity> children = image.getChildren();
            while (image.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
        }

        @Test
        void Check_Attributes() {
            int numberOfValues = image.getNumberOfAttributes();
            List<String> expectedAttributeValues = OmeroServer.getImageAttributeValue(image);

            List<String> attributesValues = IntStream.range(0, numberOfValues)
                    .mapToObj(i -> image.getAttributeValue(i))
                    .toList();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedAttributeValues, attributesValues);
        }

        protected abstract long getImageId();
    }

    @Nested
    class RGBImage extends GenericImage {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            client = OmeroServer.createClient(userType);
            image = client.getApisHandler().getImage(OmeroServer.getRGBImage(userType).getId()).get();
        }

        @Override
        protected long getImageId() {
            return OmeroServer.getRGBImage(userType).getId();
        }
    }

    @Nested
    class ComplexImage extends GenericImage {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            client = OmeroServer.createClient(userType);
            image = client.getApisHandler().getImage(OmeroServer.getComplexImage(userType).getId()).get();
        }

        @Override
        protected long getImageId() {
            return OmeroServer.getComplexImage(userType).getId();
        }
    }
}
