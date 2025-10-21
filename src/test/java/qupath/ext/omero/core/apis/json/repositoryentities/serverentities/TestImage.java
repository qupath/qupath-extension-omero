package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroPhysicalSize;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.Server;
import qupath.lib.images.servers.PixelType;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class TestImage extends OmeroServer {

    abstract static class GenericUser {

        protected static UserType userType;
        protected static Client client;         // creating a client is necessary for the check children functions to work
        protected static Image image;

        @AfterAll
        static void closeClient() throws Exception {
            client.close();
        }

        @Test
        void Check_Has_Children() {
            boolean expectedChildren = false;

            boolean hasChildren = image.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;

            List<? extends RepositoryEntity> children = image.getChildren(experimenterId, groupId).get();

            Assertions.assertTrue(children.isEmpty());
        }

        @Test
        void Check_Children_Filtered_By_Experimenter() throws InterruptedException, ExecutionException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;

            List<? extends RepositoryEntity> children = image.getChildren(experimenterId, groupId).get();

            Assertions.assertTrue(children.isEmpty());
        }

        @Test
        void Check_Children_Filtered_By_Group() throws InterruptedException, ExecutionException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();

            List<? extends RepositoryEntity> children = image.getChildren(experimenterId, groupId).get();

            Assertions.assertTrue(children.isEmpty());
        }

        @Test
        void Check_Children_Filtered_By_Experimenter_And_Group() throws InterruptedException, ExecutionException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();

            List<? extends RepositoryEntity> children = image.getChildren(experimenterId, groupId).get();

            Assertions.assertTrue(children.isEmpty());
        }

        @Test
        void Check_Id() {
            long expectedId = OmeroServer.getImage(userType).id();

            long id = image.getId();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        void Check_Owner_Id() {
            long expectedId = OmeroServer.getEntityOwner(userType).id();

            long id = image.getOwnerId();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        void Check_Group_Id() {
            long expectedId = OmeroServer.getEntityGroup(userType).id();

            long id = image.getGroupId();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        void Check_Name() {
            String expectedName = OmeroServer.getImageName(userType);

            String name = image.getName().orElseThrow();

            Assertions.assertEquals(expectedName, name);
        }

        @Test
        void Check_Acquisition_Date() {
            Date expectedAcquisitionDate = OmeroServer.getImageAcquisitionDate();

            Date acquisitionDate = image.getAcquisitionDate().orElse(null);

            Assertions.assertEquals(expectedAcquisitionDate, acquisitionDate);
        }

        @Test
        void Check_Size_Mebibyte() {
            double expectedSize = OmeroServer.getImageSizeMebibyte(userType);

            double size = image.getSizeMebibyte().orElseThrow();

            Assertions.assertEquals(expectedSize, size);
        }

        @Test
        void Check_Size_X() {
            int expectedSize = OmeroServer.getImageSizeX();

            int size = image.getSizeX();

            Assertions.assertEquals(expectedSize, size);
        }

        @Test
        void Check_Size_Y() {
            int expectedSize = OmeroServer.getImageSizeY();

            int size = image.getSizeY();

            Assertions.assertEquals(expectedSize, size);
        }

        @Test
        void Check_Size_Z() {
            int expectedSize = OmeroServer.getImageSizeZ();

            int size = image.getSizeZ();

            Assertions.assertEquals(expectedSize, size);
        }

        @Test
        void Check_Size_C() {
            int expectedSize = OmeroServer.getImageSizeC();

            int size = image.getSizeC();

            Assertions.assertEquals(expectedSize, size);
        }

        @Test
        void Check_Size_T() {
            int expectedSize = OmeroServer.getImageSizeT();

            int size = image.getSizeT();

            Assertions.assertEquals(expectedSize, size);
        }

        @Test
        void Check_Physical_Size_X() {
            OmeroPhysicalSize expectedSize = OmeroServer.getImagePhysicalSizeX();

            OmeroPhysicalSize size = image.getPhysicalSizeX().orElse(null);

            Assertions.assertEquals(expectedSize, size);
        }

        @Test
        void Check_Physical_Size_Y() {
            OmeroPhysicalSize expectedSize = OmeroServer.getImagePhysicalSizeY();

            OmeroPhysicalSize size = image.getPhysicalSizeY().orElse(null);

            Assertions.assertEquals(expectedSize, size);
        }

        @Test
        void Check_Physical_Size_Z() {
            OmeroPhysicalSize expectedSize = OmeroServer.getImagePhysicalSizeZ();

            OmeroPhysicalSize size = image.getPhysicalSizeZ().orElse(null);

            Assertions.assertEquals(expectedSize, size);
        }

        @Test
        void Check_Pixel_Type() {
            PixelType expectedPixelType = OmeroServer.getImagePixelType(userType);

            PixelType pixelType = image.getPixelType().orElse(null);

            Assertions.assertEquals(expectedPixelType, pixelType);
        }
    }

    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createImage() throws ExecutionException, InterruptedException {
            userType = UserType.AUTHENTICATED;
            client = OmeroServer.createClient(userType);
            Server server = client.getServer().get();

            Project project = server.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Project)
                    .map(p -> (Project) p)
                    .filter(p -> p.getId() == OmeroServer.getProject(userType).id())
                    .findAny()
                    .orElseThrow();

            Dataset dataset = project.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Dataset)
                    .map(d -> (Dataset) d)
                    .filter(d -> d.getId() == OmeroServer.getDataset(userType).id())
                    .findAny()
                    .orElseThrow();

            image = dataset.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Image)
                    .map(i -> (Image) i)
                    .filter(i -> i.getId() == OmeroServer.getImage(userType).id())
                    .findAny()
                    .orElseThrow();
        }
    }

    @Nested
    class UnauthenticatedUser extends GenericUser {

        @BeforeAll
        static void createImage() throws ExecutionException, InterruptedException {
            userType = UserType.UNAUTHENTICATED;
            client = OmeroServer.createClient(userType);
            Server server = client.getServer().get();

            Project project = server.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Project)
                    .map(p -> (Project) p)
                    .filter(p -> p.getId() == OmeroServer.getProject(userType).id())
                    .findAny()
                    .orElseThrow();

            Dataset dataset = project.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Dataset)
                    .map(d -> (Dataset) d)
                    .filter(d -> d.getId() == OmeroServer.getDataset(userType).id())
                    .findAny()
                    .orElseThrow();

            image = dataset.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof Image)
                    .map(i -> (Image) i)
                    .filter(i -> i.getId() == OmeroServer.getImage(userType).id())
                    .findAny()
                    .orElseThrow();
        }
    }
}
