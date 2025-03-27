package qupath.ext.omero.core.entities.repositoryentities.serverentities.image;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestPixelInfo {

    @Test
    void Check_Empty() {
        Assertions.assertDoesNotThrow(() -> new Gson().fromJson("{}", PixelInfo.class));
    }

    @Test
    void Check_Width() {
        PixelInfo pixelInfo = createPixelInfo();
        int expectedWidth = 1234;

        int width = pixelInfo.width();

        Assertions.assertEquals(expectedWidth, width);
    }

    @Test
    void Check_Height() {
        PixelInfo pixelInfo = createPixelInfo();
        int expectedHeight = 789;

        int height = pixelInfo.height();

        Assertions.assertEquals(expectedHeight, height);
    }

    @Test
    void Check_Size_Z() {
        PixelInfo pixelInfo = createPixelInfo();
        int expectedSizeZ = 12;

        int sizeZ = pixelInfo.sizeZ();

        Assertions.assertEquals(expectedSizeZ, sizeZ);
    }

    @Test
    void Check_Number_Of_Channels() {
        PixelInfo pixelInfo = createPixelInfo();
        int expectedNumberOfChannels = 3;

        int numberOfChannels = pixelInfo.numberOfChannels();

        Assertions.assertEquals(expectedNumberOfChannels, numberOfChannels);
    }

    @Test
    void Check_Number_Of_Time_Points() {
        PixelInfo pixelInfo = createPixelInfo();
        int expectedNumberOfTimePoints = 2;

        int numberOfTimePoints = pixelInfo.numberOfTimePoints();

        Assertions.assertEquals(expectedNumberOfTimePoints, numberOfTimePoints);
    }

    @Test
    void Check_Physical_Size_X() {
        PixelInfo pixelInfo = createPixelInfo();
        PhysicalSize expectedPhysicalSizeX = new PhysicalSize("μm", 45.63);

        PhysicalSize physicalSizeX = pixelInfo.physicalSizeX();

        Assertions.assertEquals(expectedPhysicalSizeX, physicalSizeX);
    }

    @Test
    void Check_Physical_Size_Y() {
        PixelInfo pixelInfo = createPixelInfo();
        PhysicalSize expectedPhysicalSizeY = new PhysicalSize("μm", 87.2);

        PhysicalSize physicalSizeY = pixelInfo.physicalSizeY();

        Assertions.assertEquals(expectedPhysicalSizeY, physicalSizeY);
    }

    @Test
    void Check_Physical_Size_Z() {
        PixelInfo pixelInfo = createPixelInfo();
        PhysicalSize expectedPhysicalSizeZ = new PhysicalSize("mm", 1.2);

        PhysicalSize physicalSizeZ = pixelInfo.physicalSizeZ();

        Assertions.assertEquals(expectedPhysicalSizeZ, physicalSizeZ);
    }

    @Test
    void Check_Pixel_Type() {
        PixelInfo pixelInfo = createPixelInfo();
        String expectedPixelType = "float";

        String pixelType = pixelInfo.imageType().value();

        Assertions.assertEquals(expectedPixelType, pixelType);
    }

    private PixelInfo createPixelInfo() {
        String json = """
                {
                    "SizeX": 1234,
                    "SizeY": 789,
                    "SizeZ": 12,
                    "SizeC": 3,
                    "SizeT": 2,
                    "PhysicalSizeX": {
                        "Symbol": "μm",
                        "Value": 45.63
                    },
                    "PhysicalSizeY": {
                        "Symbol": "μm",
                        "Value": 87.2
                    },
                    "PhysicalSizeZ": {
                        "Symbol": "mm",
                        "Value": 1.2
                    },
                    "Type": {
                        "value": "float"
                    },
                    "Channels": [
                        {
                            "Name": "Channel 1"
                        },
                        {
                            "Name": "Channel 2"
                        }
                    ]
                }
                """;
        return new Gson().fromJson(json, PixelInfo.class);
    }
}
