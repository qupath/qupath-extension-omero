package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.Server;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class TestPlateAcquisition extends OmeroServer {

    private static final OmeroServer.UserType userType = OmeroServer.UserType.AUTHENTICATED;
    private static final Client client = OmeroServer.createClient(userType);
    protected static PlateAcquisition plateAcquisition;

    @BeforeAll
    static void createClient() throws ExecutionException, InterruptedException {
        Server server = client.getServer().get();

        List<? extends RepositoryEntity> serverChildren = server.getChildren();
        while (server.isPopulatingChildren()) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
        Screen screen = serverChildren.stream()
                .filter(child -> child instanceof Screen)
                .map(s -> (Screen) s)
                .toList()
                .getLast();

        List<? extends RepositoryEntity> plateChildren = screen.getChildren();
        while (screen.isPopulatingChildren()) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
        Plate plate = plateChildren.stream()
                .filter(child -> child instanceof Plate)
                .map(p -> (Plate) p)
                .findAny()
                .orElseThrow();

        List<? extends RepositoryEntity> plateAcquisitionChildren = plate.getChildren();
        while (plate.isPopulatingChildren()) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
        plateAcquisition = plateAcquisitionChildren.stream()
                .filter(child -> child instanceof PlateAcquisition)
                .map(p -> (PlateAcquisition) p)
                .findAny()
                .orElseThrow();
    }

    @AfterAll
    static void removeClient() throws Exception {
        client.close();
    }

    @Test
    void Check_Owner() {
        Owner expectedOwner = OmeroServer.getOwnerOfEntity(plateAcquisition);

        Owner owner = plateAcquisition.getOwner();

        Assertions.assertEquals(expectedOwner, owner);
    }

    @Test
    void Check_Group_Id() {
        long expectedGroupId = OmeroServer.getGroupOfEntity(plateAcquisition).getId();

        long groupId = plateAcquisition.getGroupId();

        Assertions.assertEquals(expectedGroupId, groupId);
    }

    @Test
    void Check_Group_Name() {
        String expectedGroupName = OmeroServer.getGroupOfEntity(plateAcquisition).getName();

        String groupName = plateAcquisition.getGroupName();

        Assertions.assertEquals(expectedGroupName, groupName);
    }

    @Test
    void Check_Has_Children() {
        boolean expectedChildren = !OmeroServer.getWellsInPlateAcquisition(plateAcquisition).isEmpty();

        boolean hasChildren = plateAcquisition.hasChildren();

        Assertions.assertEquals(expectedChildren, hasChildren);
    }

    @Test
    void Check_Children() throws InterruptedException {
        List<? extends RepositoryEntity> expectedChildren = OmeroServer.getWellsInPlateAcquisition(plateAcquisition);

        List<? extends RepositoryEntity> children = plateAcquisition.getChildren();
        while (plateAcquisition.isPopulatingChildren()) {
            TimeUnit.MILLISECONDS.sleep(50);
        }

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
    }

    @Test
    void Check_Attributes() {
        int numberOfValues = plateAcquisition.getNumberOfAttributes();
        List<String> expectedAttributeValues = OmeroServer.getPlateAcquisitionAttributeValue(plateAcquisition);

        List<String> attributesValues = IntStream.range(0, numberOfValues)
                .mapToObj(i -> plateAcquisition.getAttributeValue(i))
                .toList();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedAttributeValues, attributesValues);
    }
}
