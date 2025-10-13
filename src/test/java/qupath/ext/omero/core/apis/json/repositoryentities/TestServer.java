package qupath.ext.omero.core.apis.json.repositoryentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.apis.json.permissions.Experimenter;
import qupath.ext.omero.core.apis.json.permissions.ExperimenterGroup;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.ServerEntity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

public class TestServer extends OmeroServer {

    abstract static class GenericUser {

        protected static OmeroServer.UserType userType;
        protected static ApisHandler apisHandler;
        protected static Server server;

        @AfterAll
        static void closeApisHandler() throws Exception {
            apisHandler.close();
        }

        @Test
        void Check_Children() throws InterruptedException, ExecutionException {
            long experimenterId = -1;
            long groupId = -1;
            List<Long> expectedChildrenIds = Stream.of(
                    OmeroServer.getOrphanedDatasetIds(userType, experimenterId, groupId),
                    OmeroServer.getProjectIds(userType, experimenterId, groupId),
                    OmeroServer.getScreenIds(userType, experimenterId, groupId),
                    OmeroServer.getOrphanedPlateIds(userType, experimenterId, groupId)
            ).flatMap(Collection::stream).toList();     // orphaned folder is not taken into account

            List<? extends RepositoryEntity> children = server.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(ServerEntity.class::isInstance)
                            .map(ServerEntity.class::cast)
                            .map(ServerEntity::getId)
                            .toList()
            );
        }

        @Test
        void Check_Children_Filtered_By_Experimenter() throws InterruptedException, ExecutionException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            List<Long> expectedChildrenIds = Stream.of(
                    OmeroServer.getOrphanedDatasetIds(userType, experimenterId, groupId),
                    OmeroServer.getProjectIds(userType, experimenterId, groupId),
                    OmeroServer.getScreenIds(userType, experimenterId, groupId),
                    OmeroServer.getOrphanedPlateIds(userType, experimenterId, groupId)
            ).flatMap(Collection::stream).toList();     // orphaned folder is not taken into account

            List<? extends RepositoryEntity> children = server.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(ServerEntity.class::isInstance)
                            .map(ServerEntity.class::cast)
                            .map(ServerEntity::getId)
                            .toList()
            );
        }

        @Test
        void Check_Children_Filtered_By_Group() throws InterruptedException, ExecutionException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedChildrenIds = Stream.of(
                    OmeroServer.getOrphanedDatasetIds(userType, experimenterId, groupId),
                    OmeroServer.getProjectIds(userType, experimenterId, groupId),
                    OmeroServer.getScreenIds(userType, experimenterId, groupId),
                    OmeroServer.getOrphanedPlateIds(userType, experimenterId, groupId)
            ).flatMap(Collection::stream).toList();     // orphaned folder is not taken into account

            List<? extends RepositoryEntity> children = server.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(ServerEntity.class::isInstance)
                            .map(ServerEntity.class::cast)
                            .map(ServerEntity::getId)
                            .toList()
            );
        }

        @Test
        void Check_Children_Filtered_By_Experimenter_And_Group() throws InterruptedException, ExecutionException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedChildrenIds = Stream.of(
                    OmeroServer.getOrphanedDatasetIds(userType, experimenterId, groupId),
                    OmeroServer.getProjectIds(userType, experimenterId, groupId),
                    OmeroServer.getScreenIds(userType, experimenterId, groupId),
                    OmeroServer.getOrphanedPlateIds(userType, experimenterId, groupId)
            ).flatMap(Collection::stream).toList();     // orphaned folder is not taken into account

            List<? extends RepositoryEntity> children = server.getChildren(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(
                    expectedChildrenIds,
                    children.stream()
                            .filter(ServerEntity.class::isInstance)
                            .map(ServerEntity.class::cast)
                            .map(ServerEntity::getId)
                            .toList()
            );
        }

        @Test
        void Check_Default_Group() {
            ExperimenterGroup expectedDefaultGroup = OmeroServer.getDefaultGroup(userType);

            ExperimenterGroup defaultGroup = server.getDefaultGroup();

            Assertions.assertEquals(expectedDefaultGroup, defaultGroup);
        }

        @Test
        void Check_Connected_Experimenter() {
            Experimenter expectedConnectedOwner = OmeroServer.getConnectedExperimenter(userType);

            Experimenter connectedOwner = server.getConnectedExperimenter();

            Assertions.assertEquals(expectedConnectedOwner, connectedOwner);
        }

        @Test
        void Check_Groups() {
            List<ExperimenterGroup> expectedGroups = OmeroServer.getGroups(userType);

            List<ExperimenterGroup> groups = server.getGroups();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedGroups, groups);
        }

        @Test
        void Check_Experimenter() {
            List<Experimenter> expectedExperimenters = OmeroServer.getExperimenters(userType);

            List<Experimenter> experimenters = server.getExperimenters();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedExperimenters, experimenters);
        }
    }

    @Nested
    class AdminUser extends GenericUser {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException, URISyntaxException {
            userType = UserType.ADMIN;
            apisHandler = new ApisHandler(
                    URI.create(OmeroServer.getWebServerURI()),
                    OmeroServer.getCredentials(userType)
            );
            server = new Server(apisHandler);
        }
    }

    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException, URISyntaxException {
            userType = UserType.AUTHENTICATED;
            apisHandler = new ApisHandler(
                    URI.create(OmeroServer.getWebServerURI()),
                    OmeroServer.getCredentials(userType)
            );
            server = new Server(apisHandler);
        }
    }

    @Nested
    class UnauthenticatedUser extends GenericUser {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException, URISyntaxException {
            userType = UserType.UNAUTHENTICATED;
            apisHandler = new ApisHandler(
                    URI.create(OmeroServer.getWebServerURI()),
                    OmeroServer.getCredentials(userType)
            );
            server = new Server(apisHandler);
        }
    }
}
