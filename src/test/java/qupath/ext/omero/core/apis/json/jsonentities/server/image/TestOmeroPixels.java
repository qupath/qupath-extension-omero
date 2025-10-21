package qupath.ext.omero.core.apis.json.jsonentities.server.image;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.lib.images.servers.PixelType;

import java.util.Optional;

public class TestOmeroPixels {

    @Test
    void Check_Size_X_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPixels(
                        "type",
                        null,
                        2,
                        3,
                        4,
                        5,
                        new OmeroPhysicalSize("s", 2.423),
                        new OmeroPhysicalSize("d", 232d),
                        new OmeroPhysicalSize("f", 23.25),
                        new OmeroImageType("value")
                )
        );
    }

    @Test
    void Check_Size_Y_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPixels(
                        "type",
                        1,
                        null,
                        3,
                        4,
                        5,
                        new OmeroPhysicalSize("s", 2.423),
                        new OmeroPhysicalSize("d", 232d),
                        new OmeroPhysicalSize("f", 23.25),
                        new OmeroImageType("value")
                )
        );
    }

    @Test
    void Check_Size_Z_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPixels(
                        "type",
                        1,
                        2,
                        null,
                        4,
                        5,
                        new OmeroPhysicalSize("s", 2.423),
                        new OmeroPhysicalSize("d", 232d),
                        new OmeroPhysicalSize("f", 23.25),
                        new OmeroImageType("value")
                )
        );
    }

    @Test
    void Check_Size_C_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPixels(
                        "type",
                        1,
                        2,
                        3,
                        null,
                        5,
                        new OmeroPhysicalSize("s", 2.423),
                        new OmeroPhysicalSize("d", 232d),
                        new OmeroPhysicalSize("f", 23.25),
                        new OmeroImageType("value")
                )
        );
    }

    @Test
    void Check_Size_T_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPixels(
                        "type",
                        1,
                        2,
                        3,
                        4,
                        null,
                        new OmeroPhysicalSize("s", 2.423),
                        new OmeroPhysicalSize("d", 232d),
                        new OmeroPhysicalSize("f", 23.25),
                        new OmeroImageType("value")
                )
        );
    }

    @Test
    void Check_Image_Type_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPixels(
                        "type",
                        1,
                        2,
                        3,
                        4,
                        5,
                        new OmeroPhysicalSize("s", 2.423),
                        new OmeroPhysicalSize("d", 232d),
                        new OmeroPhysicalSize("f", 23.25),
                        null
                )
        );
    }

    @Test
    void Check_Pixel_Type_When_Unknown() {
        OmeroPixels omeroPixels = new OmeroPixels(
                "type",
                1,
                2,
                3,
                4,
                5,
                new OmeroPhysicalSize("s", 2.423),
                new OmeroPhysicalSize("d", 232d),
                new OmeroPhysicalSize("f", 23.25),
                new OmeroImageType("unknown")
        );

        Optional<PixelType> pixelType = omeroPixels.pixelType();

        Assertions.assertTrue(pixelType.isEmpty());
    }

    @Test
    void Check_Pixel_Type_When_Known() {
        PixelType expectedPixelType = PixelType.UINT8;
        OmeroPixels omeroPixels = new OmeroPixels(
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

        PixelType pixelType = omeroPixels.pixelType().orElseThrow();

        Assertions.assertEquals(expectedPixelType, pixelType);
    }

    @Test
    void Check_Size_When_Pixel_Type_Unknown() {
        OmeroPixels omeroPixels = new OmeroPixels(
                "type",
                1,
                2,
                3,
                4,
                5,
                new OmeroPhysicalSize("s", 2.423),
                new OmeroPhysicalSize("d", 232d),
                new OmeroPhysicalSize("f", 23.25),
                new OmeroImageType("unknown")
        );

        Optional<Double> sizeMebibyte = omeroPixels.sizeMebibyte();

        Assertions.assertTrue(sizeMebibyte.isEmpty());
    }

    @Test
    void Check_Size_When_Pixel_Type_Known() {
        double expectedSizeMebibyte = 2 * 3 * 4 * 5 * 2 / (1024*1024d);
        OmeroPixels omeroPixels = new OmeroPixels(
                "type",
                1,
                2,
                3,
                4,
                5,
                new OmeroPhysicalSize("s", 2.423),
                new OmeroPhysicalSize("d", 232d),
                new OmeroPhysicalSize("f", 23.25),
                new OmeroImageType("uint16")
        );

        double sizeMebibyte = omeroPixels.sizeMebibyte().orElseThrow();

        Assertions.assertEquals(expectedSizeMebibyte, sizeMebibyte);
    }

    @Test
    void Check_Created_From_Json() {
        OmeroPixels expectedOmeroPixels = new OmeroPixels(
                "type",
                1,
                2,
                3,
                4,
                5,
                new OmeroPhysicalSize("s", 2.423),
                new OmeroPhysicalSize("d", 232d),
                new OmeroPhysicalSize("f", 23.25),
                new OmeroImageType("unknown")
        );

        OmeroPixels omeroPixels = new Gson().fromJson(
                """
                {
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
                        "value": "unknown"
                    }
                }
                """,
                OmeroPixels.class
        );

        Assertions.assertEquals(expectedOmeroPixels, omeroPixels);
    }
}
