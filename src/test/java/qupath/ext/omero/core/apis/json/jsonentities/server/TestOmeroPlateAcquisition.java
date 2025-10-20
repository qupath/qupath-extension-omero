package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

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
                        new OmeroSimpleDetails(
                                new OmeroSimpleExperimenter(234L),
                                new OmeroSimpleExperimenterGroup(987L)
                        )
                )
        );
    }

    @Test
    void Check_Details_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPlateAcquisition(
                        "",
                        534L,
                        "",
                        List.of(),
                        53L,
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroPlateAcquisition expectedOmeroPlateAcquisition = new OmeroPlateAcquisition(
                "type",
                534L,
                "name",
                List.of(3, 60),
                53L,
                new OmeroSimpleDetails(
                        new OmeroSimpleExperimenter(234L),
                        new OmeroSimpleExperimenterGroup(987L)
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
                    "omero:details": {
                        "owner": {
                            "@id": 234
                        },
                        "group": {
                            "@id": 987
                        }
                    }
                }
                """,
                OmeroPlateAcquisition.class
        );

        Assertions.assertEquals(expectedOmeroPlateAcquisition, omeroPlateAcquisition);
    }
}
