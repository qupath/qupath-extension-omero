package qupath.ext.omero.core.entities.repositoryentities.serverentities.image;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestImageType {

    @Test
    void Check_Empty() {
        ImageType imageType = new Gson().fromJson("{}", ImageType.class);

        String value = imageType.value();

        Assertions.assertNull(value);
    }

    @Test
    void Check_Value() {
        ImageType imageType = createImageType();

        String value = imageType.value();

        Assertions.assertEquals("uint8", value);
    }

    private ImageType createImageType() {
        String json = """
                {
                    "value": "uint8"
                }
                """;
        return new Gson().fromJson(json, ImageType.class);
    }
}
