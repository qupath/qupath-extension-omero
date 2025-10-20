package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroSimpleExperimenterGroup {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroSimpleExperimenterGroup(
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroSimpleExperimenterGroup expectedOmeroSimpleGroup = new OmeroSimpleExperimenterGroup(
                54L
        );

        OmeroSimpleExperimenterGroup omeroSimpleGroup = new Gson().fromJson(
                """
                {
                    "@id": 54
                }
                """,
                OmeroSimpleExperimenterGroup.class
        );

        Assertions.assertEquals(expectedOmeroSimpleGroup, omeroSimpleGroup);
    }
}
