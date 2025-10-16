package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;

import java.util.Optional;

public class TestOmeroPlate {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPlate(
                        "",
                        null,
                        "",
                        4,
                        53,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                )
        );
    }

    @Test
    void Check_Owner_When_Not_Present() {
        OmeroPlate omeroPlate = new OmeroPlate(
                "",
                534L,
                "",
                3,
                53,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        Optional<OmeroExperimenter> owner = omeroPlate.owner();

        Assertions.assertTrue(owner.isEmpty());
    }

    @Test
    void Check_Owner_When_Present() {
        OmeroExperimenter expectedOwner = new OmeroExperimenter(
                "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter",
                54L,
                "first",
                "middle",
                "last"
        );
        OmeroPlate omeroPlate = new OmeroPlate(
                "",
                534L,
                "",
                3,
                53,
                new OmeroDetails(
                        expectedOwner,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroExperimenter owner = omeroPlate.owner().orElseThrow();

        Assertions.assertEquals(expectedOwner, owner);
    }

    @Test
    void Check_Group_When_Not_Present() {
        OmeroPlate omeroPlate = new OmeroPlate(
                "",
                534L,
                "",
                4,
                53,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        Optional<OmeroExperimenterGroup> group = omeroPlate.group();

        Assertions.assertTrue(group.isEmpty());
    }

    @Test
    void Check_Group_When_Present() {
        OmeroExperimenterGroup expectedGroup = new OmeroExperimenterGroup(
                "http://www.openmicroscopy.org/Schemas/OME/2016-06#ExperimenterGroup",
                65L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, false, false)
                ),
                "group name",
                "http://someUrl.com"
        );
        OmeroPlate omeroPlate = new OmeroPlate(
                "",
                534L,
                "",
                3,
                53,
                new OmeroDetails(
                        null,
                        expectedGroup,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroExperimenterGroup group = omeroPlate.group().orElseThrow();

        Assertions.assertEquals(expectedGroup, group);
    }

    @Test
    void Check_Created_From_Json() {
        OmeroPlate expectedOmeroPlate = new OmeroPlate(
                "type",
                534L,
                "name",
                3,
                53,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroPlate omeroPlate = new Gson().fromJson(
                """
                {
                    "@type": "type",
                    "@id": 534,
                    "Name": "name",
                    "Columns": 3,
                    "Rows": 53,
                    "omero:details": {
                        "permissions": {
                            "isGroupWrite": false,
                            "isGroupRead": true,
                            "isGroupAnnotate": true
                        }
                    }
                }
                """,
                OmeroPlate.class
        );

        Assertions.assertEquals(expectedOmeroPlate, omeroPlate);
    }
}
