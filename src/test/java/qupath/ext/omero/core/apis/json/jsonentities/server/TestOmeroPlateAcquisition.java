package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;

import java.util.List;
import java.util.Optional;

public class TestOmeroPlateAcquisition {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPlateAcquisition(
                        "",
                        null,
                        "",
                        List.of(),
                        53L,
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
        OmeroPlateAcquisition omeroPlateAcquisition = new OmeroPlateAcquisition(
                "",
                534L,
                "",
                List.of(),
                53L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        Optional<OmeroExperimenter> owner = omeroPlateAcquisition.owner();

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
        OmeroPlateAcquisition omeroPlateAcquisition = new OmeroPlateAcquisition(
                "",
                534L,
                "",
                List.of(),
                53L,
                new OmeroDetails(
                        expectedOwner,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroExperimenter owner = omeroPlateAcquisition.owner().orElseThrow();

        Assertions.assertEquals(expectedOwner, owner);
    }

    @Test
    void Check_Group_When_Not_Present() {
        OmeroPlateAcquisition omeroPlateAcquisition = new OmeroPlateAcquisition(
                "",
                534L,
                "",
                List.of(),
                53L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        Optional<OmeroExperimenterGroup> group = omeroPlateAcquisition.group();

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
        OmeroPlateAcquisition omeroPlateAcquisition = new OmeroPlateAcquisition(
                "",
                534L,
                "",
                List.of(),
                53L,
                new OmeroDetails(
                        null,
                        expectedGroup,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroExperimenterGroup group = omeroPlateAcquisition.group().orElseThrow();

        Assertions.assertEquals(expectedGroup, group);
    }

    @Test
    void Check_Created_From_Json() {
        OmeroPlateAcquisition expectedOmeroPlateAcquisition = new OmeroPlateAcquisition(
                "type",
                534L,
                "name",
                List.of(3, 60),
                53L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroPlateAcquisition omeroPlateAcquisition = new Gson().fromJson(
                """
                {
                    "@type": "type",
                    "@id": 534,
                    "Name": "name",
                    "omero:wellsampleIndex": [3, 60],
                    "StartTime": 53,
                    "omero:details:": {
                        "permissions": {
                            "isGroupWrite": false,
                            "isGroupRead": true,
                            "isGroupAnnotate": true
                        }
                    }
                }
                """,
                OmeroPlateAcquisition.class
        );

        Assertions.assertEquals(expectedOmeroPlateAcquisition, omeroPlateAcquisition);
    }
}
