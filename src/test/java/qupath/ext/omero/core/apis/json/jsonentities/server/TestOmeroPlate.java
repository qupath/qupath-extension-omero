package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
                        new OmeroSimpleDetails(
                                new OmeroSimpleExperimenter(456L),
                                new OmeroSimpleExperimenterGroup(23L)
                        )
                )
        );
    }

    @Test
    void Check_Details_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPlate(
                        "",
                        534L,
                        "",
                        4,
                        53,
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroPlate expectedOmeroPlate = new OmeroPlate(
                "type",
                534L,
                "name",
                3,
                53,
                new OmeroSimpleDetails(
                        new OmeroSimpleExperimenter(456L),
                        new OmeroSimpleExperimenterGroup(23L)
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
                        "owner": {
                            "@id": 456
                        },
                        "group": {
                            "@id": 23
                        }
                    }
                }
                """,
                OmeroPlate.class
        );

        Assertions.assertEquals(expectedOmeroPlate, omeroPlate);
    }
}
