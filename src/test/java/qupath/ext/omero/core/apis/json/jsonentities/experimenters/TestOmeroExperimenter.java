package qupath.ext.omero.core.apis.json.jsonentities.experimenters;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroExperimenter {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new OmeroExperimenter(
                        "",
                        null,
                        "",
                        "",
                        ""
                )
        );
    }

    @Test
    void Check_Full_Name() {
        String expectedFullName = "first middle last";
        OmeroExperimenter omeroExperimenter = new OmeroExperimenter(
                "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter",
                54L,
                "first",
                "middle",
                "last"
        );

        String fullName = omeroExperimenter.fullName();

        Assertions.assertEquals(expectedFullName, fullName);
    }

    @Test
    void Check_Full_Name_With_First_Missing() {
        String expectedFullName = "middle last";
        OmeroExperimenter omeroExperimenter = new OmeroExperimenter(
                "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter",
                54L,
                null,
                "middle",
                "last"
        );

        String fullName = omeroExperimenter.fullName();

        Assertions.assertEquals(expectedFullName, fullName);
    }

    @Test
    void Check_Full_Name_With_Middle_Missing() {
        String expectedFullName = "first last";
        OmeroExperimenter omeroExperimenter = new OmeroExperimenter(
                "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter",
                54L,
                "first",
                null,
                "last"
        );

        String fullName = omeroExperimenter.fullName();

        Assertions.assertEquals(expectedFullName, fullName);
    }

    @Test
    void Check_Full_Name_With_Middle_And_Last_Missing() {
        String expectedFullName = "first";
        OmeroExperimenter omeroExperimenter = new OmeroExperimenter(
                "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter",
                54L,
                "first",
                null,
                null
        );

        String fullName = omeroExperimenter.fullName();

        Assertions.assertEquals(expectedFullName, fullName);
    }

    @Test
    void Check_Full_Name_With_All_Missing() {
        String expectedFullName = "";
        OmeroExperimenter omeroExperimenter = new OmeroExperimenter(
                "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter",
                54L,
                null,
                null,
                null
        );

        String fullName = omeroExperimenter.fullName();

        Assertions.assertEquals(expectedFullName, fullName);
    }

    @Test
    void Check_Created_From_Json() {
        OmeroExperimenter expectedOmeroExperimenter = new OmeroExperimenter(
                "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter",
                54L,
                "first",
                "middle",
                "last"
        );

        OmeroExperimenter omeroExperimenter = new Gson().fromJson(
                """
                {
                    "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter",
                    "@id": 54,
                    "FirstName": "first",
                    "MiddleName": "middle",
                    "LastName": "last"
                }
                """,
                OmeroExperimenter.class
        );

        Assertions.assertEquals(expectedOmeroExperimenter, omeroExperimenter);
    }
}
