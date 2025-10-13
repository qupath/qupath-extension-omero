package qupath.ext.omero.core.apis.json.jsonentities.server.image;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroImageType {

    @Test
    void Check_Value_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroImageType(
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroImageType expectedOmeroImageType = new OmeroImageType(
                "value"
        );

        OmeroImageType omeroImageType = new Gson().fromJson(
                """
                {
                    "value": "value"
                }
                """,
                OmeroImageType.class
        );

        Assertions.assertEquals(expectedOmeroImageType, omeroImageType);
    }
}
