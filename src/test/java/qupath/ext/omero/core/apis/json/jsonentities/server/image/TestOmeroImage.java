package qupath.ext.omero.core.apis.json.jsonentities.server.image;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;
import qupath.lib.images.servers.PixelType;

import java.util.Optional;

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
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
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
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                )
        );
    }

    @Test
    void Check_Owner_When_Not_Present() {
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        Optional<OmeroExperimenter> owner = omeroImage.owner();

        Assertions.assertTrue(owner.isEmpty());
    }

    @Test
    void Check_Owner_When_Present() {
        OmeroExperimenter expectedOwner = new OmeroExperimenter(
                "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter",
                54L,
                "first",
                "middle",
                "last"
        );
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        expectedOwner,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroExperimenter owner = omeroImage.owner().orElseThrow();

        Assertions.assertEquals(expectedOwner, owner);
    }

    @Test
    void Check_Group_When_Not_Present() {
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        Optional<OmeroExperimenterGroup> group = omeroImage.group();

        Assertions.assertTrue(group.isEmpty());
    }

    @Test
    void Check_Group_When_Present() {
        OmeroExperimenterGroup expectedGroup = new OmeroExperimenterGroup(
                "http://www.openmicroscopy.org/Schemas/OME/2016-06#ExperimenterGroup",
                65L,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, false, false)
                ),
                "group name",
                "http://someUrl.com"
        );
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        expectedGroup,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroExperimenterGroup group = omeroImage.group().orElseThrow();

        Assertions.assertEquals(expectedGroup, group);
    }

    @Test
    void Check_Size_X() {
        int expectedSize = 1;
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        int size = omeroImage.sizeX();

        Assertions.assertEquals(expectedSize, size);
    }

    @Test
    void Check_Size_Y() {
        int expectedSize = 2;
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        int size = omeroImage.sizeY();

        Assertions.assertEquals(expectedSize, size);
    }

    @Test
    void Check_Size_Z() {
        int expectedSize = 3;
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        int size = omeroImage.sizeZ();

        Assertions.assertEquals(expectedSize, size);
    }

    @Test
    void Check_Size_C() {
        int expectedSize = 4;
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        int size = omeroImage.sizeC();

        Assertions.assertEquals(expectedSize, size);
    }

    @Test
    void Check_Size_T() {
        int expectedSize = 5;
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        int size = omeroImage.sizeT();

        Assertions.assertEquals(expectedSize, size);
    }

    @Test
    void Check_Pixel_Type() {
        PixelType expectedPixelType = PixelType.UINT8;
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        PixelType pixelType = omeroImage.pixelType().orElseThrow();

        Assertions.assertEquals(expectedPixelType, pixelType);
    }

    @Test
    void Check_Size_Mebibyte() {
        double expectedSizeMebibyte = 2 * 3 * 4 * 5 / (1024*1024d);
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        double sizeMebibyte = omeroImage.sizeMebibyte().orElseThrow();

        Assertions.assertEquals(expectedSizeMebibyte, sizeMebibyte);
    }

    @Test
    void Check_Physical_Size_X() {
        OmeroPhysicalSize expectedPhysicalSize = new OmeroPhysicalSize("s", 2.423);
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroPhysicalSize physicalSize = omeroImage.physicalSizeX().orElseThrow();

        Assertions.assertEquals(expectedPhysicalSize, physicalSize);
    }

    @Test
    void Check_Physical_Size_Y() {
        OmeroPhysicalSize expectedPhysicalSize = new OmeroPhysicalSize("d", 232d);
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroPhysicalSize physicalSize = omeroImage.physicalSizeY().orElseThrow();

        Assertions.assertEquals(expectedPhysicalSize, physicalSize);
    }

    @Test
    void Check_Physical_Size_Z() {
        OmeroPhysicalSize expectedPhysicalSize = new OmeroPhysicalSize("f", 23.25);
        OmeroImage omeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroPhysicalSize physicalSize = omeroImage.physicalSizeZ().orElseThrow();

        Assertions.assertEquals(expectedPhysicalSize, physicalSize);
    }

    @Test
    void Check_Created_From_Json() {
        OmeroImage expectedOmeroImage = new OmeroImage(
                "type",
                4432L,
                "name",
                24234L,
                createOmeroPixels(),
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
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
                        "permissions": {
                            "isGroupWrite": false,
                            "isGroupRead": true,
                            "isGroupAnnotate": true
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
