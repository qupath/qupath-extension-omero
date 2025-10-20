package qupath.ext.omero.core.apis.iviewer.imageentities;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroImageChannel {

    @Test
    void Check_Created_From_Json() {
        OmeroImageChannel expectedOmeroImageChannel = new OmeroImageChannel(
                "label",
                "color",
                new OmeroWindow(234.234d, 5d)
        );

        OmeroImageChannel omeroImageChannel = new Gson().fromJson(
                """
                {
                    "label": "label",
                    "color": "color",
                    "window": {
                        "start": 234.234,
                        "end": 5
                    }
                }
                """,
                OmeroImageChannel.class
        );

        Assertions.assertEquals(expectedOmeroImageChannel, omeroImageChannel);
    }
}
