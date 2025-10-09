package qupath.ext.omero.core.apis.json.permissions;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;

public class TestExperimenter {

    @Test
    void Check_Id_Of_All_Experimenter() {
        long expectedId = -1;
        Experimenter allExperimenter = Experimenter.getAllExperimenters();

        long id = allExperimenter.getId();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Id() {
        long expectedId = 64;
        Experimenter experimenter = new Experimenter(new OmeroExperimenter(
                null,
                expectedId,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        long id = experimenter.getId();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Full_Name() {
        String expectedFullName = "first middle last";
        Experimenter experimenter = new Experimenter(new OmeroExperimenter(
                "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter",
                54L,
                "first",
                "middle",
                "last",
                "email",
                "institution",
                "user"
        ));

        String fullName = experimenter.getFullName();

        Assertions.assertEquals(expectedFullName, fullName);
    }
}
