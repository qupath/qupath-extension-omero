package qupath.ext.omero.core.apis.json.permissions;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;

import java.util.List;

public class TestExperimenterGroup {

    @Test
    void Check_Id_Of_All_Groups() {
        long expectedId = -1;
        ExperimenterGroup allGroups = ExperimenterGroup.getAllGroups();

        long id = allGroups.getId();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Id() {
        long expectedId = 54;
        ExperimenterGroup group = new ExperimenterGroup(
                new OmeroExperimenterGroup(
                        null,
                        expectedId,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, false)
                        ),
                        "name",
                        ""
                ),
                List.of(new Experimenter(new OmeroExperimenter(
                        null,
                        64L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )))
        );

        long id = group.getId();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Name() {
        String expectedName = "name";
        ExperimenterGroup group = new ExperimenterGroup(
                new OmeroExperimenterGroup(
                        null,
                        54L,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, false)
                        ),
                        expectedName,
                        ""
                ),
                List.of(new Experimenter(new OmeroExperimenter(
                        null,
                        64L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )))
        );

        String name = group.getName().orElseThrow();

        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Permission_Level() {
        PermissionLevel expectedPermissionLevel = PermissionLevel.READ_ONLY;
        ExperimenterGroup group = new ExperimenterGroup(
                new OmeroExperimenterGroup(
                        null,
                        54L,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, false)
                        ),
                        "name",
                        ""
                ),
                List.of(new Experimenter(new OmeroExperimenter(
                        null,
                        64L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )))
        );

        PermissionLevel permissionLevel = group.getPermissionLevel();

        Assertions.assertEquals(expectedPermissionLevel, permissionLevel);
    }

    @Test
    void Check_Experimenters() {
        List<Experimenter> expectedExperimenters = List.of(new Experimenter(new OmeroExperimenter(
                null,
                64L,
                null,
                null,
                null,
                null,
                null,
                null
        )));
        ExperimenterGroup group = new ExperimenterGroup(
                new OmeroExperimenterGroup(
                        null,
                        54L,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, false)
                        ),
                        "name",
                        ""
                ),
                expectedExperimenters
        );

        List<Experimenter> experimenters = group.getExperimenters();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedExperimenters, experimenters);
    }
}
