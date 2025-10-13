package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroImage;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroImageType;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroPhysicalSize;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroPixels;

import java.util.List;

public class TestOmeroWellSample {

    @Test
    void Check_Created_From_Json() {
        OmeroWellSample expectedOmeroWellSample = new OmeroWellSample(
                "type",
                new OmeroImage(
                        "type",
                        4432L,
                        "name",
                        24234L,
                        new OmeroPixels(
                                "type",
                                1,
                                2,
                                3,
                                4,
                                5,
                                new OmeroPhysicalSize("s", 2.423),
                                new OmeroPhysicalSize("d", 232d),
                                new OmeroPhysicalSize("f", 23.25),
                                new OmeroImageType("uint8")
                        ),
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
                        )
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
                        "@id": 4432,
                        "Name": "name",
                        "AcquisitionDate": 24234,
                        "Pixels": {
                            "@type": "type",
                            "SizeX": 1,
                            "SizeY": 2,
                            "SizeZ": 3,
                            "SizeC": 4,
                            "SizeT": 5,
                            "PhysicalSizeX": {
                                "Symbol": "s",
                                "Value": 2.423
                            },
                            "PhysicalSizeY": {
                                "Symbol": "d",
                                "Value": 232
                            },
                            "PhysicalSizeZ": {
                                "Symbol": "f",
                                "Value": 23.25
                            },
                            "Type": {
                                "value": "uint8"
                            }
                        },
                        "omero:details:": {
                            "permissions": {
                                "isGroupWrite": false,
                                "isGroupRead": true,
                                "isGroupAnnotate": true
                            }
                        }
                    },
                    "PlateAcquisition": {
                        "@type": "type",
                        "@id": 534,
                        "Name": "name",
                        "omero:wellsampleIndex": [3, 60],
                        "StartTime": 53,
                        "omero:details:": {
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
