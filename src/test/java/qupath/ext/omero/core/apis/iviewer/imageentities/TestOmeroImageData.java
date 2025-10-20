package qupath.ext.omero.core.apis.iviewer.imageentities;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestOmeroImageData {

    @Test
    void Check_Created_From_Json() {
        OmeroImageData expectedOmeroImageData = new OmeroImageData(
                new OmeroImageMetadata("name"),
                List.of(
                        new OmeroImageChannel("label1", "color1", new OmeroWindow(234d, 45.6d)),
                        new OmeroImageChannel("label2", "color2", new OmeroWindow(0d, 5.657567d))
                )
        );

        OmeroImageData omeroImageData = new Gson().fromJson(
                """
                {
                    "meta": {
                        "imageName": "name"
                    },
                    "channels": [
                        {
                            "label": "label1",
                            "color": "color1",
                            "window": {
                                "start": 234,
                                "end": 45.6
                            }
                        },
                        {
                            "label": "label2",
                            "color": "color2",
                            "window": {
                                "start": 0,
                                "end": 5.657567
                            }
                        }
                    ]
                }
                """,
                OmeroImageData.class
        );

        Assertions.assertEquals(expectedOmeroImageData, omeroImageData);
    }
}
