package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroSimpleImage;

import java.util.List;

public class TestOmeroWellSample {

    @Test
    void Check_Created_From_Json() {
        OmeroWellSample expectedOmeroWellSample = new OmeroWellSample(
                "type",
                new OmeroSimpleImage(
                        "type",
                        4432L
                ),
                new OmeroPlateAcquisition(
                        "type",
                        534L,
                        "name",
                        List.of(3, 60),
                        53L,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                )
        );

        OmeroWellSample omeroWellSample = new Gson().fromJson(
                """
                {
                    "@type": "type",
                    "Image": {
                        "@type": "type",
                        "@id": 4432
                    },
                    "PlateAcquisition": {
                        "@type": "type",
                        "@id": 534,
                        "Name": "name",
                        "omero:wellsampleIndex": [3, 60],
                        "StartTime": 53,
                        "omero:details": {
                            "permissions": {
                                "isGroupWrite": false,
                                "isGroupRead": true,
                                "isGroupAnnotate": true
                            }
                        }
                    }
                }
                """,
                OmeroWellSample.class
        );

        Assertions.assertEquals(expectedOmeroWellSample, omeroWellSample);
    }
}
