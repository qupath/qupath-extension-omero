package qupath.ext.omero.core.apis.json.repositoryentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Image;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class TestOrphanedFolder extends OmeroServer {

    abstract static class GenericUser {

        protected static OmeroServer.UserType userType;
        protected static ApisHandler apisHandler;
        protected static OrphanedFolder orphanedFolder;

        @AfterAll
        static void closeApisHandler() throws Exception {
            apisHandler.close();
        }

        @Test
        void Check_Children() throws InterruptedException, ExecutionException {
            long experimenterId = -1;
            long groupId = -1;
            List<Long> expectedChildrenIds = OmeroServer.getOrphanedImageIds(userType, experimenterId, groupId);

            List<? extends RepositoryEntity> children = orphanedFolder.getChildren(experimenterId, groupId).get();

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
            List<Long> expectedChildrenIds = OmeroServer.getOrphanedImageIds(userType, experimenterId, groupId);

            List<? extends RepositoryEntity> children = orphanedFolder.getChildren(experimenterId, groupId).get();

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
            List<Long> expectedChildrenIds = OmeroServer.getOrphanedImageIds(userType, experimenterId, groupId);

            List<? extends RepositoryEntity> children = orphanedFolder.getChildren(experimenterId, groupId).get();

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
            List<Long> expectedChildrenIds = OmeroServer.getOrphanedImageIds(userType, experimenterId, groupId);

            List<? extends RepositoryEntity> children = orphanedFolder.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(Image.class::isInstance)
                            .map(Image.class::cast)
                            .map(Image::getId)
                            .toList()
            );
        }
    }

    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createOrphanedFolder() throws ExecutionException, InterruptedException, URISyntaxException {
            userType = UserType.AUTHENTICATED;
            apisHandler = new ApisHandler(
                    URI.create(OmeroServer.getWebServerURI()),
                    OmeroServer.getCredentials(userType)
            );
            orphanedFolder = new OrphanedFolder(apisHandler);
        }
    }

    @Nested
    class UnauthenticatedUser extends GenericUser {

        @BeforeAll
        static void createOrphanedFolder() throws ExecutionException, InterruptedException, URISyntaxException {
            userType = UserType.UNAUTHENTICATED;
            apisHandler = new ApisHandler(
                    URI.create(OmeroServer.getWebServerURI()),
                    OmeroServer.getCredentials(userType)
            );
            orphanedFolder = new OrphanedFolder(apisHandler);
        }
    }
}
