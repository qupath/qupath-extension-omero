package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroSimpleDetails {

    @Test
    void Check_Experimenter_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroSimpleDetails(
                        null,
                        new OmeroSimpleExperimenterGroup(448L)
                )
        );
    }

    @Test
    void Check_Group_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroSimpleDetails(
                        new OmeroSimpleExperimenter(234L),
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroSimpleDetails expectedOmeroSimpleDetails = new OmeroSimpleDetails(
                new OmeroSimpleExperimenter(234L),
                new OmeroSimpleExperimenterGroup(448L)
        );

        OmeroSimpleDetails omeroSimpleDetails = new Gson().fromJson(
                """
                {
                    "owner": {
                        "@id": 234
                    },
                    "group": {
                        "@id": 448
                    }
                }
                """,
                OmeroSimpleDetails.class
        );

        Assertions.assertEquals(expectedOmeroSimpleDetails, omeroSimpleDetails);
    }
}
