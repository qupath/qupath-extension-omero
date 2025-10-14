package qupath.ext.omero.core.apis.json;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtils;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.commonentities.shapes.Shape;
import qupath.ext.omero.core.apis.json.permissions.ExperimenterGroup;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Image;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Well;

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

            TestUtils.assertCollectionsEqualsWithoutOrder(expectedGroups, groups);
        }

        @Test
        void Check_Groups_Of_User() throws ExecutionException, InterruptedException {
            long userId = OmeroServer.getConnectedExperimenter(userType).getId();
            List<ExperimenterGroup> expectedGroups = OmeroServer.getGroups(userType);

            List<ExperimenterGroup> groups = jsonApi.getGroups(userId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(expectedGroups, groups);
        }

        @Test
        void Check_Projects() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            List<Long> expectedProjectIds = OmeroServer.getProjectIds(userType, experimenterId, groupId);

            List<Project> projects = jsonApi.getProjects(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedProjectIds,
                    projects.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Projects_Of_Experimenter() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            List<Long> expectedProjectIds = OmeroServer.getProjectIds(userType, experimenterId, groupId);

            List<Project> projects = jsonApi.getProjects(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedProjectIds,
                    projects.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Projects_Of_Group() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedProjectIds = OmeroServer.getProjectIds(userType, experimenterId, groupId);

            List<Project> projects = jsonApi.getProjects(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedProjectIds,
                    projects.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Projects_Of_Experimenter_And_Group() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedProjectIds = OmeroServer.getProjectIds(userType, experimenterId, groupId);

            List<Project> projects = jsonApi.getProjects(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedProjectIds,
                    projects.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Project() throws ExecutionException, InterruptedException {
            long expectedId = OmeroServer.getProject(userType).id();

            Project project = jsonApi.getProject(expectedId).get();

            Assertions.assertEquals(expectedId, project.getId());
        }

        @Test
        void Check_Invalid_Project() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getProject(invalidId).get()
            );
        }

        @Test
        void Check_Datasets() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            long projectId = OmeroServer.getProject(userType).id();
            List<Long> expectedDatasetIds = OmeroServer.getProjectDatasetIds(userType);

            List<Dataset> datasets = jsonApi.getDatasets(projectId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedDatasetIds,
                    datasets.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Datasets_Of_Experimenter() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            long projectId = OmeroServer.getProject(userType).id();
            List<Long> expectedDatasetIds = OmeroServer.getProjectDatasetIds(userType);

            List<Dataset> datasets = jsonApi.getDatasets(projectId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedDatasetIds,
                    datasets.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Datasets_Of_Group() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            long projectId = OmeroServer.getProject(userType).id();
            List<Long> expectedDatasetIds = OmeroServer.getProjectDatasetIds(userType);

            List<Dataset> datasets = jsonApi.getDatasets(projectId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedDatasetIds,
                    datasets.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Datasets_Of_Experimenter_And_Group() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            long projectId = OmeroServer.getProject(userType).id();
            List<Long> expectedDatasetIds = OmeroServer.getProjectDatasetIds(userType);

            List<Dataset> datasets = jsonApi.getDatasets(projectId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedDatasetIds,
                    datasets.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Datasets_With_Invalid_Parent() {
            long invalidProjectId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getDatasets(invalidProjectId, -1, -1).get()
            );
        }

        @Test
        void Check_Orphaned_Datasets() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            List<Long> expectedDatasetIds = OmeroServer.getOrphanedDatasetIds(userType, experimenterId, groupId);

            List<Dataset> datasets = jsonApi.getOrphanedDatasets(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedDatasetIds,
                    datasets.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Orphaned_Datasets_Of_Experimenter() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            List<Long> expectedDatasetIds = OmeroServer.getOrphanedDatasetIds(userType, experimenterId, groupId);

            List<Dataset> datasets = jsonApi.getOrphanedDatasets(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedDatasetIds,
                    datasets.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Orphaned_Datasets_Of_Group() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedDatasetIds = OmeroServer.getOrphanedDatasetIds(userType, experimenterId, groupId);

            List<Dataset> datasets = jsonApi.getOrphanedDatasets(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedDatasetIds,
                    datasets.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Orphaned_Datasets_Of_Experimenter_And_Group() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedDatasetIds = OmeroServer.getOrphanedDatasetIds(userType, experimenterId, groupId);

            List<Dataset> datasets = jsonApi.getOrphanedDatasets(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedDatasetIds,
                    datasets.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Dataset() throws ExecutionException, InterruptedException {
            long expectedId = OmeroServer.getDataset(userType).id();

            Dataset dataset = jsonApi.getDataset(expectedId).get();

            Assertions.assertEquals(expectedId, dataset.getId());
        }

        @Test
        void Check_Invalid_Dataset() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getDataset(invalidId).get()
            );
        }

        @Test
        void Check_Images() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            long datasetId = OmeroServer.getDataset(userType).id();
            List<Long> expectedImageIds = OmeroServer.getDatasetImageIds(userType);

            List<Image> images = jsonApi.getImages(datasetId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedImageIds,
                    images.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Images_Of_Experimenter() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            long datasetId = OmeroServer.getDataset(userType).id();
            List<Long> expectedImageIds = OmeroServer.getDatasetImageIds(userType);

            List<Image> images = jsonApi.getImages(datasetId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedImageIds,
                    images.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Images_Of_Group() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            long datasetId = OmeroServer.getDataset(userType).id();
            List<Long> expectedImageIds = OmeroServer.getDatasetImageIds(userType);

            List<Image> images = jsonApi.getImages(datasetId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedImageIds,
                    images.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Images_Of_Experimenter_And_Group() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            long datasetId = OmeroServer.getDataset(userType).id();
            List<Long> expectedImageIds = OmeroServer.getDatasetImageIds(userType);

            List<Image> images = jsonApi.getImages(datasetId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedImageIds,
                    images.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Images_With_Invalid_Parent() {
            long invalidDatasetId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getImages(invalidDatasetId, -1, -1).get()
            );
        }

        @Test
        void Check_Orphaned_Images() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            List<Long> expectedImageIds = OmeroServer.getOrphanedImageIds(userType, experimenterId, groupId);

            List<Image> images = jsonApi.getOrphanedImages(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedImageIds,
                    images.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Orphaned_Images_Of_Experimenter() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            List<Long> expectedImageIds = OmeroServer.getOrphanedImageIds(userType, experimenterId, groupId);

            List<Image> images = jsonApi.getOrphanedImages(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedImageIds,
                    images.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Orphaned_Images_Of_Group() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedImageIds = OmeroServer.getOrphanedImageIds(userType, experimenterId, groupId);

            List<Image> images = jsonApi.getOrphanedImages(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedImageIds,
                    images.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Orphaned_Images_Of_Experimenter_And_Group() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedImageIds = OmeroServer.getOrphanedImageIds(userType, experimenterId, groupId);

            List<Image> images = jsonApi.getOrphanedImages(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedImageIds,
                    images.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Image() throws ExecutionException, InterruptedException {
            long expectedId = OmeroServer.getImage(userType).id();

            Image image = jsonApi.getImage(expectedId).get();

            Assertions.assertEquals(expectedId, image.getId());
        }

        @Test
        void Check_Invalid_Image() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getImage(invalidId).get()
            );
        }

        @Test
        void Check_Screens() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            List<Long> expectedScreenIds = OmeroServer.getScreenIds(userType, experimenterId, groupId);

            List<Screen> screens = jsonApi.getScreens(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedScreenIds,
                    screens.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Screens_Of_Experimenter() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            List<Long> expectedScreenIds = OmeroServer.getScreenIds(userType, experimenterId, groupId);

            List<Screen> screens = jsonApi.getScreens(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedScreenIds,
                    screens.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Screens_Of_Group() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedScreenIds = OmeroServer.getScreenIds(userType, experimenterId, groupId);

            List<Screen> screens = jsonApi.getScreens(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedScreenIds,
                    screens.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Screens_Of_Experimenter_And_Group() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedScreenIds = OmeroServer.getScreenIds(userType, experimenterId, groupId);

            List<Screen> screens = jsonApi.getScreens(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedScreenIds,
                    screens.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Screen() throws ExecutionException, InterruptedException {
            long expectedId = OmeroServer.getScreen(userType).id();

            Screen screen = jsonApi.getScreen(expectedId).get();

            Assertions.assertEquals(expectedId, screen.getId());
        }

        @Test
        void Check_Invalid_Screen() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getScreen(invalidId).get()
            );
        }

        @Test
        void Check_Plates() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            long screenId = OmeroServer.getScreen(userType).id();
            List<Long> expectedPlateIds = OmeroServer.getScreenPlateIds(userType);

            List<Plate> plates = jsonApi.getPlates(screenId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedPlateIds,
                    plates.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Plates_Of_Experimenter() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            long screenId = OmeroServer.getScreen(userType).id();
            List<Long> expectedPlateIds = OmeroServer.getScreenPlateIds(userType);

            List<Plate> plates = jsonApi.getPlates(screenId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedPlateIds,
                    plates.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Plates_Of_Group() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            long screenId = OmeroServer.getScreen(userType).id();
            List<Long> expectedPlateIds = OmeroServer.getScreenPlateIds(userType);

            List<Plate> plates = jsonApi.getPlates(screenId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedPlateIds,
                    plates.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Plates_Of_Experimenter_And_Group() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            long screenId = OmeroServer.getScreen(userType).id();
            List<Long> expectedPlateIds = OmeroServer.getScreenPlateIds(userType);

            List<Plate> plates = jsonApi.getPlates(screenId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedPlateIds,
                    plates.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Plates_With_Invalid_Parent() {
            long invalidScreenId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getPlates(invalidScreenId, -1, -1).get()
            );
        }

        @Test
        void Check_Orphaned_Plates() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            List<Long> expectedPlateIds = OmeroServer.getOrphanedPlateIds(userType, experimenterId, groupId);

            List<Plate> plates = jsonApi.getOrphanedPlates(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedPlateIds,
                    plates.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Orphaned_Plates_Of_Experimenter() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            List<Long> expectedPlateIds = OmeroServer.getOrphanedPlateIds(userType, experimenterId, groupId);

            List<Plate> plates = jsonApi.getOrphanedPlates(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedPlateIds,
                    plates.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Orphaned_Plates_Of_Group() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedPlateIds = OmeroServer.getOrphanedPlateIds(userType, experimenterId, groupId);

            List<Plate> plates = jsonApi.getOrphanedPlates(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedPlateIds,
                    plates.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Orphaned_Plates_Of_Experimenter_And_Group() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            List<Long> expectedPlateIds = OmeroServer.getOrphanedPlateIds(userType, experimenterId, groupId);

            List<Plate> plates = jsonApi.getOrphanedPlates(experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedPlateIds,
                    plates.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Plate() throws ExecutionException, InterruptedException {
            long expectedId = OmeroServer.getPlate(userType).id();

            Plate plate = jsonApi.getPlate(expectedId).get();

            Assertions.assertEquals(expectedId, plate.getId());
        }

        @Test
        void Check_Invalid_Plate() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getPlate(invalidId).get()
            );
        }

        @Test
        void Check_Plate_Acquisitions() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            long plateId = OmeroServer.getPlate(userType).id();
            List<Long> expectedPlateAcquisitionIds = OmeroServer.getPlatePlateAcquisitionIds(userType);

            List<PlateAcquisition> plateAcquisitions = jsonApi.getPlateAcquisitions(plateId, experimenterId, groupId, 0).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedPlateAcquisitionIds,
                    plateAcquisitions.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Plate_Acquisitions_Of_Experimenter() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            long plateId = OmeroServer.getPlate(userType).id();
            List<Long> expectedPlateAcquisitionIds = OmeroServer.getPlatePlateAcquisitionIds(userType);

            List<PlateAcquisition> plateAcquisitions = jsonApi.getPlateAcquisitions(plateId, experimenterId, groupId, 0).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedPlateAcquisitionIds,
                    plateAcquisitions.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Plate_Acquisitions_Of_Group() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            long plateId = OmeroServer.getPlate(userType).id();
            List<Long> expectedPlateAcquisitionIds = OmeroServer.getPlatePlateAcquisitionIds(userType);

            List<PlateAcquisition> plateAcquisitions = jsonApi.getPlateAcquisitions(plateId, experimenterId, groupId, 0).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedPlateAcquisitionIds,
                    plateAcquisitions.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Plate_Acquisitions_Of_Experimenter_And_Group() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            long plateId = OmeroServer.getPlate(userType).id();
            List<Long> expectedPlateAcquisitionIds = OmeroServer.getPlatePlateAcquisitionIds(userType);

            List<PlateAcquisition> plateAcquisitions = jsonApi.getPlateAcquisitions(plateId, experimenterId, groupId, 0).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedPlateAcquisitionIds,
                    plateAcquisitions.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Plate_Acquisitions_With_Invalid_Parent() {
            long invalidPlateId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getPlateAcquisitions(invalidPlateId, -1, -1, 0).get()
            );
        }

        @Test
        void Check_Plate_Acquisition() throws ExecutionException, InterruptedException {
            long expectedId = OmeroServer.getPlateAcquisition(userType).id();

            PlateAcquisition plateAcquisition = jsonApi.getPlateAcquisition(expectedId).get();

            Assertions.assertEquals(expectedId, plateAcquisition.getId());
        }

        @Test
        void Check_Invalid_Plate_Acquisition() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getPlateAcquisition(invalidId).get()
            );
        }

        @Test
        void Check_Wells_From_Plate() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            long plateId = OmeroServer.getPlate(userType).id();
            List<Long> expectedWellIds = OmeroServer.getPlateWellIds(userType);

            List<Well> wells = jsonApi.getWellsFromPlate(plateId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedWellIds,
                    wells.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Wells_From_Plate_Of_Experimenter() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            long plateId = OmeroServer.getPlate(userType).id();
            List<Long> expectedWellIds = OmeroServer.getPlateWellIds(userType);

            List<Well> wells = jsonApi.getWellsFromPlate(plateId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedWellIds,
                    wells.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Wells_From_Plate_Of_Group() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            long plateId = OmeroServer.getPlate(userType).id();
            List<Long> expectedWellIds = OmeroServer.getPlateWellIds(userType);

            List<Well> wells = jsonApi.getWellsFromPlate(plateId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedWellIds,
                    wells.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Wells_From_Plate_Of_Experimenter_And_Group() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            long plateId = OmeroServer.getPlate(userType).id();
            List<Long> expectedWellIds = OmeroServer.getPlateWellIds(userType);

            List<Well> wells = jsonApi.getWellsFromPlate(plateId, experimenterId, groupId).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedWellIds,
                    wells.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Wells_From_Plate_With_Invalid_Parent() {
            long invalidPlateId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getWellsFromPlate(invalidPlateId, -1, -1).get()
            );
        }

        @Test
        void Check_Wells_From_Plate_Acquisition() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = -1;
            long plateAcquisitionId = OmeroServer.getPlateAcquisition(userType).id();
            List<Long> expectedWellIds = OmeroServer.getPlateAcquisitionWellIds(userType);

            List<Well> wells = jsonApi.getWellsFromPlateAcquisition(plateAcquisitionId, experimenterId, groupId, 0).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedWellIds,
                    wells.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Wells_From_Plate_Acquisition_Of_Experimenter() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = -1;
            long plateAcquisitionId = OmeroServer.getPlateAcquisition(userType).id();
            List<Long> expectedWellIds = OmeroServer.getPlateAcquisitionWellIds(userType);

            List<Well> wells = jsonApi.getWellsFromPlateAcquisition(plateAcquisitionId, experimenterId, groupId, 0).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedWellIds,
                    wells.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Wells_From_Plate_Acquisition_Of_Group() throws ExecutionException, InterruptedException {
            long experimenterId = -1;
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            long plateAcquisitionId = OmeroServer.getPlateAcquisition(userType).id();
            List<Long> expectedWellIds = OmeroServer.getPlateAcquisitionWellIds(userType);

            List<Well> wells = jsonApi.getWellsFromPlateAcquisition(plateAcquisitionId, experimenterId, groupId, 0).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedWellIds,
                    wells.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Wells_From_Plate_Acquisition_Of_Experimenter_And_Group() throws ExecutionException, InterruptedException {
            long experimenterId = OmeroServer.getConnectedExperimenter(userType).getId();
            long groupId = OmeroServer.getDefaultGroup(userType).getId();
            long plateAcquisitionId = OmeroServer.getPlateAcquisition(userType).id();
            List<Long> expectedWellIds = OmeroServer.getPlateAcquisitionWellIds(userType);

            List<Well> wells = jsonApi.getWellsFromPlateAcquisition(plateAcquisitionId, experimenterId, groupId, 0).get();

            TestUtils.assertCollectionsEqualsWithoutOrder(
                    expectedWellIds,
                    wells.stream().map(ServerEntity::getId).toList()
            );
        }

        @Test
        void Check_Wells_From_Plate_Acquisition_With_Invalid_Parent() {
            long invalidPlateAcquisitionId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getWellsFromPlateAcquisition(invalidPlateAcquisitionId, -1, -1, 0).get()
            );
        }

        @Test
        void Check_Well() throws ExecutionException, InterruptedException {
            long expectedId = OmeroServer.getWell(userType).id();

            Well well = jsonApi.getWell(expectedId).get();

            Assertions.assertEquals(expectedId, well.getId());
        }

        @Test
        void Check_Invalid_Well() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> jsonApi.getWell(invalidId).get()
            );
        }

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
