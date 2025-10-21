package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroDataset {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroDataset(
                        "",
                        null,
                        "",
                        "",
                        53,
                        new OmeroSimpleDetails(
                                new OmeroSimpleExperimenter(23L),
                                new OmeroSimpleExperimenterGroup(98L)
                        )
                )
        );
    }

    @Test
    void Check_Child_Count_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroDataset(
                        "",
                        534L,
                        "",
                        "",
                        null,
                        new OmeroSimpleDetails(
                                new OmeroSimpleExperimenter(23L),
                                new OmeroSimpleExperimenterGroup(98L)
                        )
                )
        );
    }

    @Test
    void Check_Details_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroDataset(
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
        OmeroDataset expectedOmeroDataset = new OmeroDataset(
                "type",
                534L,
                "name",
                "description",
                53,
                new OmeroSimpleDetails(
                        new OmeroSimpleExperimenter(23L),
                        new OmeroSimpleExperimenterGroup(98L)
                )
        );

        OmeroDataset omeroDataset = new Gson().fromJson(
                """
                {
                    "@type": "type",
                    "@id": 534,
                    "Name": "name",
                    "Description": "description",
                    "omero:childCount": 53,
                    "omero:details": {
                        "owner": {
                            "@id": 23
                        },
                        "group": {
                            "@id": 98
                        }
                    }
                }
                """,
                OmeroDataset.class
        );

        Assertions.assertEquals(expectedOmeroDataset, omeroDataset);
    }
}
