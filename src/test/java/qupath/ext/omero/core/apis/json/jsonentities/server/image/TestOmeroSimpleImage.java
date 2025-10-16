package qupath.ext.omero.core.apis.json.jsonentities.server.image;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroSimpleImage {

    @Test
    void Check_ID_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroSimpleImage(
                        "type",
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroSimpleImage expectedOmeroSimpleImage = new OmeroSimpleImage(
                "type",
                4432L
        );

        OmeroSimpleImage omeroSimpleImage = new Gson().fromJson(
                """
                {
                    "@type": "type",
                    "@id": 4432
                }
                """,
                OmeroSimpleImage.class
        );

        Assertions.assertEquals(expectedOmeroSimpleImage, omeroSimpleImage);
    }
}
