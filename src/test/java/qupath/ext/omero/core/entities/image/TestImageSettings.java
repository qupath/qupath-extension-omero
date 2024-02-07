package qupath.ext.omero.core.entities.image;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;

import java.util.List;

public class TestImageSettings {

    @Test
    void Check_Name_From_Constructor() {
        String expectedName = "Image name";
        ImageSettings imageSettings = new ImageSettings(expectedName, List.of());

        String name = imageSettings.getName();

        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Channel_Settings_From_Constructor() {
        List<ChannelSettings> expectedChannelSettings = List.of(
                new ChannelSettings("Channel 1", 5.345, 23.87, Integer.parseInt("00FF00", 16)),
                new ChannelSettings("Channel 2", 90, 180, Integer.parseInt("0000FF", 16))
        );
        ImageSettings imageSettings = new ImageSettings("", expectedChannelSettings);

        List<ChannelSettings> channelSettings = imageSettings.getChannelSettings();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChannelSettings, channelSettings);
    }
    @Test
    void Check_Name_From_JSON() {
        ImageSettings imageSettings = createFromJson();
        String expectedName = "Image name";

        String name = imageSettings.getName();

        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Channel_Settings_From_JSON() {
        ImageSettings imageSettings = createFromJson();
        List<ChannelSettings> expectedChannelSettings = List.of(
                new ChannelSettings("Channel 1", 5.345, 23.87, Integer.parseInt("00FF00", 16)),
                new ChannelSettings("Channel 2", 90, 180, Integer.parseInt("0000FF", 16))
        );

        List<ChannelSettings> channelSettings = imageSettings.getChannelSettings();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChannelSettings, channelSettings);
    }

    private ImageSettings createFromJson() {
        String json = """
                {
                    "meta": {
                        "imageName": "Image name"
                    },
                    "channels": [
                        {
                            "label": "Channel 1",
                            "color": "00FF00",
                            "window": {
                                "start": 5.345,
                                "end": 23.87
                            }
                        },
                        {
                            "label": "Channel 2",
                            "color": "0000FF",
                            "window": {
                                "start": 90,
                                "end": 180
                            }
                        }
                    ]
                }
                """;
        return new Gson().fromJson(json, ImageSettings.class);
    }
}
