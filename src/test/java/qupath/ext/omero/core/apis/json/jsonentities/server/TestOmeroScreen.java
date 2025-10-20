package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroScreen {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroScreen(
                        "",
                        null,
                        "",
                        "",
                        53,
                        new OmeroSimpleDetails(
                                new OmeroSimpleExperimenter(234L),
                                new OmeroSimpleExperimenterGroup(9L)
                        )
                )
        );
    }

    @Test
    void Check_Child_Count_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroScreen(
                        "",
                        534L,
                        "",
                        "",
                        null,
                        new OmeroSimpleDetails(
                                new OmeroSimpleExperimenter(234L),
                                new OmeroSimpleExperimenterGroup(9L)
                        )
                )
        );
    }

    @Test
    void Check_Details_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroScreen(
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
        OmeroScreen expectedOmeroScreen = new OmeroScreen(
                "type",
                534L,
                "name",
                "description",
                53,
                new OmeroSimpleDetails(
                        new OmeroSimpleExperimenter(234L),
                        new OmeroSimpleExperimenterGroup(9L)
                )
        );

        OmeroScreen omeroScreen = new Gson().fromJson(
                """
                {
                    "@type": "type",
                    "@id": 534,
                    "Name": "name",
                    "Description": "description",
                    "omero:childCount": 53,
                    "omero:details": {
                        "owner": {
                            "@id": 234
                        },
                        "group": {
                            "@id": 9
                        }
                    }
                }
                """,
                OmeroScreen.class
        );

        Assertions.assertEquals(expectedOmeroScreen, omeroScreen);
    }
}
