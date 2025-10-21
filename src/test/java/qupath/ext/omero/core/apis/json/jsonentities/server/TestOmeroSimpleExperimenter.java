package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroSimpleExperimenter {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroSimpleExperimenter(
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroSimpleExperimenter expectedOmeroSimpleExperimenter = new OmeroSimpleExperimenter(
                54L
        );

        OmeroSimpleExperimenter omeroSimpleExperimenter = new Gson().fromJson(
                """
                {
                    "@id": 54
                }
                """,
                OmeroSimpleExperimenter.class
        );

        Assertions.assertEquals(expectedOmeroSimpleExperimenter, omeroSimpleExperimenter);
    }
}
