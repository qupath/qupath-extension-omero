package qupath.ext.omero.core.apis.iviewer.imageentities;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroImageMetadata {

    @Test
    void Check_Created_From_Json() {
        OmeroImageMetadata expectedOmeroImageMetadata = new OmeroImageMetadata(
                "name"
        );

        OmeroImageMetadata omeroImageMetadata = new Gson().fromJson(
                """
                {
                    "imageName": "name"
                }
                """,
                OmeroImageMetadata.class
        );

        Assertions.assertEquals(expectedOmeroImageMetadata, omeroImageMetadata);
    }
}
