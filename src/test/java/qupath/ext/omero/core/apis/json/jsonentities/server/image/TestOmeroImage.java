package qupath.ext.omero.core.apis.json.jsonentities.server.image;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroSimpleDetails;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroSimpleExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroSimpleExperimenterGroup;

public class TestOmeroImage {

    @Test
    void Check_ID_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroImage(
                        "type",
                        null,
                        "name",
                        24234L,
                        createOmeroPixels(),
                        new OmeroSimpleDetails(
                                new OmeroSimpleExperimenter(98L),
                                new OmeroSimpleExperimenterGroup(234L)
                        )
                )
        );
    }

    @Test
    void Check_Pixels_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroImage(
                        "type",
                        4432L,
                        "name",
                        24234L,
                        null,
                        new OmeroSimpleDetails(
                                new OmeroSimpleExperimenter(98L),
                                new OmeroSimpleExperimenterGroup(234L)
                        )
                )
        );
    }

    @Test
    void Check_Details_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroImage(
                        "type",
                        4432L,
                        "name",
                        24234L,
                        createOmeroPixels(),
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroImage expectedOmeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroSimpleDetails(
                        new OmeroSimpleExperimenter(98L),
                        new OmeroSimpleExperimenterGroup(234L)
                )
        );

        OmeroImage omeroImage = new Gson().fromJson(
                """
                {
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
                    "omero:details": {
                        "owner": {
                            "@id": 98
                        },
                        "group": {
                            "@id": 234
                        }
                    }
                }
                """,
                OmeroImage.class
        );

        Assertions.assertEquals(expectedOmeroImage, omeroImage);
    }

    private static OmeroPixels createOmeroPixels() {
        return new OmeroPixels(
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
        );
    }
}
