package qupath.ext.omero.core.entities.repositoryentities;

import org.junit.jupiter.api.*;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class TestServer extends OmeroServer {

    abstract static class GenericClient {

        protected static UserType userType;
        protected static WebClient client;
        protected static Server server;

        @AfterAll
        static void removeClient() {
            WebClients.removeClient(client);
        }

        @Test
        void Check_Has_Children() {
            boolean expectedChildren = true;    // a server always has children

            boolean hasChildren = server.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws InterruptedException, ExecutionException {
            List<? extends RepositoryEntity> expectedChildren = Stream.concat(
                    Stream.of(
                            OmeroServer.getOrphanedDatasets(userType),
                            OmeroServer.getProjects(userType),
                            OmeroServer.getScreens(userType),
                            OmeroServer.getOrphanedPlates(userType)
                    ).flatMap(Collection::stream),
                    Stream.of(OrphanedFolder.create(client.getApisHandler()).get())
            ).toList();
            while (server.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }

            List<? extends RepositoryEntity> children = server.getChildren();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
        }

        @Test
        void Check_Default_Group() {
            Group expectedDefaultGroup = OmeroServer.getDefaultGroup(userType);

            Group defaultGroup = server.getDefaultGroup();

            Assertions.assertEquals(expectedDefaultGroup, defaultGroup);
        }

        @Test
        void Check_Connected_Owner() {
            Owner expectedConnectedOwner = OmeroServer.getConnectedOwner(userType);

            Owner connectedOwner = server.getConnectedOwner();

            Assertions.assertEquals(expectedConnectedOwner, connectedOwner);
        }

        @Test
        void Check_Groups() {
            List<Group> expectedGroups = new ArrayList<>(OmeroServer.getGroups(userType));

            List<Group> groups = server.getGroups();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedGroups, groups);
        }

        @Test
        void Check_Owners() {
            List<Owner> expectedOwners = new ArrayList<>(OmeroServer.getOwners(userType));

            List<Owner> owners = server.getOwners();

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedOwners, owners);
        }
    }

    @Nested
    class UnauthenticatedClient extends GenericClient {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            userType = UserType.PUBLIC;
            client = OmeroServer.createClient(userType);
            server = client.getServer();
        }
    }

    @Nested
    class AuthenticatedClient extends GenericClient {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            userType = UserType.USER;
            client = OmeroServer.createClient(userType);
            server = client.getServer();
        }
    }
}
