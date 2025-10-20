package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
                        new OmeroSimpleDetails(
                                new OmeroSimpleExperimenter(4L),
                                new OmeroSimpleExperimenterGroup(8L)
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
                        new OmeroSimpleDetails(
                                new OmeroSimpleExperimenter(4L),
                                new OmeroSimpleExperimenterGroup(8L)
                        )
                )
        );
    }

    @Test
    void Check_Details_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroProject(
                        "",
                        534L,
                        "",
                        "",
                        53,
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroProject expectedOmeroProject = new OmeroProject(
                "type",
                534L,
                "name",
                "description",
                53,
                new OmeroSimpleDetails(
                        new OmeroSimpleExperimenter(4L),
                        new OmeroSimpleExperimenterGroup(8L)
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
                        "owner": {
                            "@id": 4
                        },
                        "group": {
                            "@id": 8
                        }
                    }
                }
                """,
                OmeroProject.class
        );

        Assertions.assertEquals(expectedOmeroProject, OmeroProject);
    }
}
