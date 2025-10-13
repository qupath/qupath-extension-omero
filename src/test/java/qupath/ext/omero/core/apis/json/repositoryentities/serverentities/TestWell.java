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
import qupath.ext.omero.core.apis.json.repositoryentities.Server;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class TestWell extends OmeroServer {

    abstract static class GenericUser {

        protected static UserType userType;
        protected static Client client;         // creating a client is necessary for the check children functions to work
        protected static Well well;

        @AfterAll
        static void closeClient() throws Exception {
            client.close();
        }

        @Test
        void Check_Has_Children() {
            boolean expectedChildren = !OmeroServer.getWellImageIds(userType).isEmpty();

            boolean hasChildren = well.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            List<Long> expectedChildrenIds = OmeroServer.getWellImageIds(userType);

            List<? extends RepositoryEntity> children = well.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
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
            List<Long> expectedChildrenIds = OmeroServer.getWellImageIds(userType);

            List<? extends RepositoryEntity> children = well.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
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
            List<Long> expectedChildrenIds = OmeroServer.getWellImageIds(userType);

            List<? extends RepositoryEntity> children = well.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
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
            List<Long> expectedChildrenIds = OmeroServer.getWellImageIds(userType);

            List<? extends RepositoryEntity> children = well.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
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
            long expectedId = OmeroServer.getWell(userType).id();

            long id = well.getId();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        void Check_Owner() {
            SimpleEntity expectedOwner = OmeroServer.getEntityOwner(userType);

            SimpleEntity owner = well.getOwner().orElseThrow();

            Assertions.assertEquals(expectedOwner, owner);
        }

        @Test
        void Check_Group() {
            SimpleEntity expectedGroup = OmeroServer.getEntityGroup(userType);

            SimpleEntity group = well.getGroup().orElseThrow();

            Assertions.assertEquals(expectedGroup, group);
        }

        @Test
        void Check_Name() {
            String expectedName = OmeroServer.getWellName(userType);

            String name = well.getName().orElseThrow();

            Assertions.assertEquals(expectedName, name);
        }

        @Test
        void Check_Attributes_Values() {
            List<String> expectedAttributes = OmeroServer.getWellAttributeValues(userType);

            List<Attribute> attributes = well.getAttributes();

            Assertions.assertEquals(
                    expectedAttributes,
                    attributes.stream().map(Attribute::value).toList()
            );
        }

        @Test
        void Check_Image_Ids() {
            List<Long> expectedImageIds = OmeroServer.getWellImageIds(userType);

            List<Long> imageIds = well.getImageIds(-1);

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedImageIds, imageIds);
        }
    }

    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createWell() throws ExecutionException, InterruptedException {
            userType = UserType.AUTHENTICATED;
            client = OmeroServer.createClient(userType);
            Server server = client.getServer().get();

            Screen screen = server.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Screen)
                    .map(p -> (Screen) p)
                    .filter(p -> p.getId() == OmeroServer.getScreen(userType).id())
                    .findAny()
                    .orElseThrow();

            Plate plate = screen.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Plate)
                    .map(d -> (Plate) d)
                    .filter(d -> d.getId() == OmeroServer.getPlate(userType).id())
                    .findAny()
                    .orElseThrow();

            well = plate.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Well)
                    .map(d -> (Well) d)
                    .filter(d -> d.getId() == OmeroServer.getWell(userType).id())
                    .findAny()
                    .orElseThrow();
        }
    }

    @Nested
    class UnauthenticatedUser extends GenericUser {

        @BeforeAll
        static void createWell() throws ExecutionException, InterruptedException {
            userType = UserType.UNAUTHENTICATED;
            client = OmeroServer.createClient(userType);
            Server server = client.getServer().get();

            Screen screen = server.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Screen)
                    .map(p -> (Screen) p)
                    .filter(p -> p.getId() == OmeroServer.getScreen(userType).id())
                    .findAny()
                    .orElseThrow();

            Plate plate = screen.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Plate)
                    .map(d -> (Plate) d)
                    .filter(d -> d.getId() == OmeroServer.getPlate(userType).id())
                    .findAny()
                    .orElseThrow();

            well = plate.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Well)
                    .map(d -> (Well) d)
                    .filter(d -> d.getId() == OmeroServer.getWell(userType).id())
                    .findAny()
                    .orElseThrow();
        }
    }
}
