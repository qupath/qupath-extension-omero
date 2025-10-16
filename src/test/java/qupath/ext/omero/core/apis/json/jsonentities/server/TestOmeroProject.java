package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;

import java.util.Optional;

public class TestOmeroProject {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroProject(
                        "",
                        null,
                        "",
                        "",
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
    void Check_Child_Count_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroProject(
                        "",
                        534L,
                        "",
                        "",
                        null,
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
        OmeroProject omeroProject = new OmeroProject(
                "",
                534L,
                "",
                "",
                53,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        Optional<OmeroExperimenter> owner = omeroProject.owner();

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
        OmeroProject omeroProject = new OmeroProject(
                "",
                534L,
                "",
                "",
                53,
                new OmeroDetails(
                        expectedOwner,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroExperimenter owner = omeroProject.owner().orElseThrow();

        Assertions.assertEquals(expectedOwner, owner);
    }

    @Test
    void Check_Group_When_Not_Present() {
        OmeroProject omeroProject = new OmeroProject(
                "",
                534L,
                "",
                "",
                53,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        Optional<OmeroExperimenterGroup> group = omeroProject.group();

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
        OmeroProject omeroProject = new OmeroProject(
                "",
                534L,
                "",
                "",
                53,
                new OmeroDetails(
                        null,
                        expectedGroup,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroExperimenterGroup group = omeroProject.group().orElseThrow();

        Assertions.assertEquals(expectedGroup, group);
    }

    @Test
    void Check_Created_From_Json() {
        OmeroProject expectedOmeroProject = new OmeroProject(
                "type",
                534L,
                "name",
                "description",
                53,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroProject OmeroProject = new Gson().fromJson(
                """
                {
                    "@type": "type",
                    "@id": 534,
                    "Name": "name",
                    "Description": "description",
                    "omero:childCount": 53,
                    "omero:details": {
                        "permissions": {
                            "isGroupWrite": false,
                            "isGroupRead": true,
                            "isGroupAnnotate": true
                        }
                    }
                }
                """,
                OmeroProject.class
        );

        Assertions.assertEquals(expectedOmeroProject, OmeroProject);
    }
}
