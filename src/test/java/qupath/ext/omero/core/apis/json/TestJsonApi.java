package qupath.ext.omero.core.apis.json;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.commonentities.shapes.Shape;
import qupath.ext.omero.core.apis.json.permissions.ExperimenterGroup;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Project;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class TestJsonApi extends OmeroServer {

    abstract static class GenericUser {

        protected static JsonApi jsonApi;
        protected static RequestSender requestSender;
        protected static UserType userType;

        @AfterAll
        static void closeRequestSender() throws Exception {
            requestSender.close();
        }

        @Test
        void Check_Server_Address() {
            String expectedServerAddress = OmeroServer.getServerAddress();

            String serverAddress = jsonApi.getServerAddress();

            Assertions.assertEquals(expectedServerAddress, serverAddress);
        }

        @Test
        void Check_Server_Port() {
            int expectedServerPort = OmeroServer.getServerPort();

            int serverPort = jsonApi.getServerPort();

            Assertions.assertEquals(expectedServerPort, serverPort);
        }

        @Test
        abstract void Check_User_Id();

        @Test
        abstract void Check_Default_Group_Id();

        @Test
        abstract void Check_Session_Uuid();

        @Test
        abstract void Check_Is_Admin();

        @Test
        abstract void Check_Is_Connected_User_Owner_Of_Group();

        @Test
        void Check_Groups() throws ExecutionException, InterruptedException {
            List<ExperimenterGroup> expectedGroups = OmeroServer.getGroups();

            List<ExperimenterGroup> groups = jsonApi.getGroups(-1).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedGroups, groups);
        }

        @Test
        void Check_Groups_Of_User() throws ExecutionException, InterruptedException {
            long userId = OmeroServer.getConnectedExperimenter(userType).getId();
            List<ExperimenterGroup> expectedGroups = OmeroServer.getGroups(userType);

            List<ExperimenterGroup> groups = jsonApi.getGroups(userId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedGroups, groups);
        }

        @Test
        void Check_Projects() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            List<Project> expectedProjects = OmeroServer.getProjects(userType, experimenterId, groupId);

            List<Project> projects = jsonApi.getProjects(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedProjects, projects);
        }

        @Test
        void Check_Projects_Of_Experimenter() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            List<Project> expectedProjects = OmeroServer.getProjects(userType, experimenterId, groupId);

            List<Project> projects = jsonApi.getProjects(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedProjects, projects);
        }

        @Test
        void Check_Projects_Of_Group() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Project> expectedProjects = OmeroServer.getProjects(userType, experimenterId, groupId);

            List<Project> projects = jsonApi.getProjects(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedProjects, projects);
        }

        @Test
        void Check_Projects_Of_Experimenter_And_Group() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Project> expectedProjects = OmeroServer.getProjects(userType, experimenterId, groupId);

            List<Project> projects = jsonApi.getProjects(experimenterId, groupId).get();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedProjects, projects);
        }

        //TODO: other server entities

        @Test
        void Check_Get_Shapes_With_Invalid_Image_Id() throws ExecutionException, InterruptedException {
            long invalidImageId = -1;
            List<Shape> expectedShapes = List.of();

            List<Shape> shapes = jsonApi.getShapes(invalidImageId, -1).get();

            Assertions.assertEquals(expectedShapes, shapes);
        }
    }

    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createClient() throws URISyntaxException, ExecutionException, InterruptedException {
            userType = UserType.AUTHENTICATED;
            requestSender = new RequestSender();
            jsonApi = new JsonApi(URI.create(OmeroServer.getWebServerURI()), requestSender, OmeroServer.getCredentials(userType));
        }

        @Test
        @Override
        void Check_User_Id() {
            long expectedId = OmeroServer.getConnectedExperimenter(userType).getId();

            long id = jsonApi.getUserId().orElseThrow();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        @Override
        void Check_Default_Group_Id() {
            long expectedDefaultGroupId = OmeroServer.getDefaultGroup(userType).getId();

            long defaultGroupId = jsonApi.getDefaultGroupId().orElseThrow();

            Assertions.assertEquals(expectedDefaultGroupId, defaultGroupId);
        }

        @Test
        @Override
        void Check_Session_Uuid() {
            String sessionUuid = jsonApi.getSessionUuid().orElse(null);

            Assertions.assertNotNull(sessionUuid);
        }

        @Test
        @Override
        void Check_Is_Admin() {
            boolean isAdmin = jsonApi.isAdmin().orElseThrow();

            Assertions.assertFalse(isAdmin);
        }

        @Test
        @Override
        void Check_Is_Connected_User_Owner_Of_Group() {
            long groupId = OmeroServer.getGroupsOwnedByUser(userType).getFirst().getId();

            Assertions.assertTrue(jsonApi.isConnectedUserOwnerOfGroup(groupId));
        }
    }

    @Nested
    class UnauthenticatedUser extends GenericUser {

        @BeforeAll
        static void createClient() throws URISyntaxException, ExecutionException, InterruptedException {
            userType = UserType.UNAUTHENTICATED;
            requestSender = new RequestSender();
            jsonApi = new JsonApi(URI.create(OmeroServer.getWebServerURI()), requestSender, OmeroServer.getCredentials(userType));
        }

        @Test
        @Override
        void Check_User_Id() {
            Optional<Long> userId = jsonApi.getUserId();

            Assertions.assertTrue(userId.isEmpty());
        }

        @Test
        @Override
        void Check_Default_Group_Id() {
            Optional<Long> defaultGroupId = jsonApi.getDefaultGroupId();

            Assertions.assertTrue(defaultGroupId.isEmpty());
        }

        @Test
        @Override
        void Check_Session_Uuid() {
            Optional<String> sessionUuid = jsonApi.getSessionUuid();

            Assertions.assertTrue(sessionUuid.isEmpty());
        }

        @Test
        @Override
        void Check_Is_Admin() {
            Optional<Boolean> isAdmin = jsonApi.isAdmin();

            Assertions.assertTrue(isAdmin.isEmpty());
        }

        @Test
        @Override
        void Check_Is_Connected_User_Owner_Of_Group() {
            long groupId = OmeroServer.getDefaultGroup(userType).getId();

            Assertions.assertFalse(jsonApi.isConnectedUserOwnerOfGroup(groupId));
        }
    }
}
