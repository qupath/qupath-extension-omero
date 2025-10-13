package qupath.ext.omero.core.apis.json.jsonentities.experimenters;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.permissions.PermissionLevel;

public class TestOmeroExperimenterGroup {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroExperimenterGroup(
                        "",
                        null,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, false, false)
                        ),
                        "",
                        ""
                )
        );
    }

    @Test
    void Check_Details_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroExperimenterGroup(
                        "",
                        65L,
                        null,
                        "",
                        ""
                )
        );
    }

    @Test
    void Check_Experimenters_Url_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroExperimenterGroup(
                        "",
                        65L,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, false, false)
                        ),
                        "",
                        null
                )
        );
    }

    @Test
    void Check_Permission_Level() {
        PermissionLevel expectedPermissionLevel = PermissionLevel.READ_ANNOTATE;
        OmeroExperimenterGroup omeroExperimenterGroup = new OmeroExperimenterGroup(
                "",
                65L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                ),
                "",
                null
        );

        PermissionLevel permissionLevel = omeroExperimenterGroup.getPermissionLevel();

        Assertions.assertEquals(expectedPermissionLevel, permissionLevel);
    }

    @Test
    void Check_Created_From_Json() {
        OmeroExperimenterGroup expectedOmeroExperimenterGroup = new OmeroExperimenterGroup(
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

        OmeroExperimenterGroup omeroExperimenterGroup = new Gson().fromJson(
                """
                {
                    "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter",
                    "@id": 65,
                    "omero:details:": {
                        "permissions": {
                            "isGroupWrite": false,
                            "isGroupRead": false,
                            "isGroupAnnotate": false
                        }
                    },
                    "Name": "group name",
                    "url:experimenters": "http://someUrl.com"
                }
                """,
                OmeroExperimenterGroup.class
        );

        Assertions.assertEquals(expectedOmeroExperimenterGroup, omeroExperimenterGroup);
    }
}
