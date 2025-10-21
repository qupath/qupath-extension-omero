package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.Server;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class TestPlateAcquisition extends OmeroServer {

    abstract static class GenericUser {

        protected static UserType userType;
        protected static Client client;         // creating a client is necessary for the check children functions to work
        protected static PlateAcquisition plateAcquisition;

        @AfterAll
        static void closeClient() throws Exception {
            client.close();
        }

        @Test
        void Check_Has_Children() {
            boolean expectedChildren = OmeroServer.getNumberOfPlateAcquisitionWell(userType, -1, -1) > 0;

            boolean hasChildren = plateAcquisition.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            int expectedNumberOfChildren = OmeroServer.getNumberOfPlateAcquisitionWell(userType, experimenterId, groupId);

            List<? extends RepositoryEntity> children = plateAcquisition.getChildren(experimenterId, groupId).get();

            Assertions.assertEquals(
                    expectedNumberOfChildren,
                    children.stream()
                            .filter(Well.class::isInstance)
                            .map(Well.class::cast)
                            .map(Well::getId)
                            .toList()
                            .size()
            );
        }

        @Test
        void Check_Children_Filtered_By_Experimenter() throws InterruptedException, ExecutionException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            int expectedNumberOfChildren = OmeroServer.getNumberOfPlateAcquisitionWell(userType, experimenterId, groupId);

            List<? extends RepositoryEntity> children = plateAcquisition.getChildren(experimenterId, groupId).get();

            Assertions.assertEquals(
                    expectedNumberOfChildren,
                    children.stream()
                            .filter(Well.class::isInstance)
                            .map(Well.class::cast)
                            .map(Well::getId)
                            .toList()
                            .size()
            );
        }

        @Test
        void Check_Children_Filtered_By_Group() throws InterruptedException, ExecutionException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            int expectedNumberOfChildren = OmeroServer.getNumberOfPlateAcquisitionWell(userType, experimenterId, groupId);

            List<? extends RepositoryEntity> children = plateAcquisition.getChildren(experimenterId, groupId).get();

            Assertions.assertEquals(
                    expectedNumberOfChildren,
                    children.stream()
                            .filter(Well.class::isInstance)
                            .map(Well.class::cast)
                            .map(Well::getId)
                            .toList()
                            .size()
            );
        }

        @Test
        void Check_Children_Filtered_By_Experimenter_And_Group() throws InterruptedException, ExecutionException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            int expectedNumberOfChildren = OmeroServer.getNumberOfPlateAcquisitionWell(userType, experimenterId, groupId);

            List<? extends RepositoryEntity> children = plateAcquisition.getChildren(experimenterId, groupId).get();

            Assertions.assertEquals(
                    expectedNumberOfChildren,
                    children.stream()
                            .filter(Well.class::isInstance)
                            .map(Well.class::cast)
                            .map(Well::getId)
                            .toList()
                            .size()
            );
        }

        @Test
        void Check_Id() {
            long expectedId = OmeroServer.getPlateAcquisition(userType).id();

            long id = plateAcquisition.getId();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        void Check_Owner_Id() {
            long expectedId = OmeroServer.getEntityOwner(userType).id();

            long id = plateAcquisition.getOwnerId();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        void Check_Group_Id() {
            long expectedId = OmeroServer.getEntityGroup(userType).id();

            long id = plateAcquisition.getGroupId();

            Assertions.assertEquals(expectedId, id);
        }

        @Test
        void Check_Name() {
            String expectedName = OmeroServer.getPlateAcquisitionName(userType);

            String name = plateAcquisition.getName().orElse(null);

            Assertions.assertEquals(expectedName, name);
        }

        @Test
        void Check_Min_Well_Sample_Index() {
            int expectedMinWellSampleIndex = OmeroServer.getPlateAcquisitionMinWellSampleIndex(userType);

            int minWellSampleIndex = plateAcquisition.getMinWellSampleIndex().orElseThrow();

            Assertions.assertEquals(expectedMinWellSampleIndex, minWellSampleIndex);
        }

        @Test
        void Check_Max_Well_Sample_Index() {
            int expectedMaxWellSampleIndex = OmeroServer.getPlateAcquisitionMaxWellSampleIndex(userType);

            int maxWellSampleIndex = plateAcquisition.getMaxWellSampleIndex().orElseThrow();

            Assertions.assertEquals(expectedMaxWellSampleIndex, maxWellSampleIndex);
        }

        @Test
        void Check_Start_Time() {
            Date expectedStartTime = OmeroServer.getPlateAcquisitionStartTime(userType);

            Date startTime = plateAcquisition.getStartTime().orElse(null);

            Assertions.assertEquals(expectedStartTime, startTime);
        }
    }

    // Only AuthenticatedUser, UnauthenticatedUser doesn't have any plate acquisition
    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createPlateAcquisition() throws ExecutionException, InterruptedException {
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

            plateAcquisition = plate.getChildren(-1, -1).get().stream()
                    .filter(child -> child instanceof PlateAcquisition)
                    .map(d -> (PlateAcquisition) d)
                    .filter(d -> d.getId() == OmeroServer.getPlateAcquisition(userType).id())
                    .findAny()
                    .orElseThrow();
        }
    }
}
