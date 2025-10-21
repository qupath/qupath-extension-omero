package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestOmeroWell {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroWell(
                        "",
                        null,
                        "",
                        List.of(),
                        53,
                        32,
                        new OmeroSimpleDetails(
                                new OmeroSimpleExperimenter(87L),
                                new OmeroSimpleExperimenterGroup(345L)
                        )
                )
        );
    }

    @Test
    void Check_Details_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroWell(
                        "",
                        534L,
                        "",
                        List.of(),
                        53,
                        32,
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroWell expectedOmeroWell = new OmeroWell(
                "type",
                534L,
                "name",
                List.of(new OmeroWellSample(null, null, null), new OmeroWellSample(null, null, null)),
                53,
                32,
                new OmeroSimpleDetails(
                        new OmeroSimpleExperimenter(87L),
                        new OmeroSimpleExperimenterGroup(345L)
                )
        );

        OmeroWell OmeroWell = new Gson().fromJson(
                """
                {
                    "@type": "type",
                    "@id": 534,
                    "Name": "name",
                    "WellSamples": [{}, {}],
                    "Column": 53,
                    "Row": 32,
                    "omero:details": {
                        "owner": {
                            "@id": 87
                        },
                        "group": {
                            "@id": 345
                        }
                    }
                }
                """,
                OmeroWell.class
        );

        Assertions.assertEquals(expectedOmeroWell, OmeroWell);
    }
}
