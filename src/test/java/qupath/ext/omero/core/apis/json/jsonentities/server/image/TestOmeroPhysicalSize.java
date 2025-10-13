package qupath.ext.omero.core.apis.json.jsonentities.server.image;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroPhysicalSize {

    @Test
    void Check_Symbol_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPhysicalSize(
                        null,
                        4.76
                )
        );
    }

    @Test
    void Check_Value_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPhysicalSize(
                        "",
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroPhysicalSize expectedOmeroPhysicalSize = new OmeroPhysicalSize(
                "symbol",
                9.34
        );

        OmeroPhysicalSize omeroPhysicalSize = new Gson().fromJson(
                """
                {
                    "Symbol": "symbol",
                    "Value": 9.34
                }
                """,
                OmeroPhysicalSize.class
        );

        Assertions.assertEquals(expectedOmeroPhysicalSize, omeroPhysicalSize);
    }
}
