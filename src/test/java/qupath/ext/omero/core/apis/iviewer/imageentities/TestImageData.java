package qupath.ext.omero.core.apis.iviewer.imageentities;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtils;
import qupath.ext.omero.core.apis.commonentities.ChannelSettings;

import java.util.List;

public class TestImageData {

    @Test
    void Check_Name() {
        String expectedName = "Image name";
        ImageData imageData = new ImageData(new OmeroImageData(
                new OmeroImageMetadata(expectedName),
                List.of()
        ));

        String name = imageData.getName();

        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Channel_Settings() {
        List<ChannelSettings> expectedChannelSettings = List.of(
                new ChannelSettings("Channel 1", 5.345, 23.87, Integer.parseInt("00FF00", 16)),
                new ChannelSettings("Channel 2", 90, 180, Integer.parseInt("0000FF", 16))
        );
        ImageData imageData = new ImageData(new OmeroImageData(
                new OmeroImageMetadata(""),
                List.of(
                        new OmeroImageChannel("Channel 1", "00FF00", new OmeroWindow(5.345, 23.87)),
                        new OmeroImageChannel("Channel 2", "0000FF", new OmeroWindow(90d, 180d))
                )
        ));

        List<ChannelSettings> channelSettings = imageData.getChannelSettings();

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedChannelSettings, channelSettings);
    }
}
