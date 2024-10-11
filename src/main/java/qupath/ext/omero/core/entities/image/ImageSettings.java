package qupath.ext.omero.core.entities.image;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Represent several settings about an image.
 */
public class ImageSettings {

    private static final Logger logger = LoggerFactory.getLogger(ImageSettings.class);
    private transient String name;
    private transient List<ChannelSettings> channelSettings;
    @SerializedName(value = "meta") private Meta meta;
    @SerializedName(value = "channels") private List<Channel> channels;

    /**
     * Create an image settings.
     *
     * @param name the name of the image
     * @param channelSettings the channel settings of the image
     */
    public ImageSettings(String name, List<ChannelSettings> channelSettings) {
        this.name = name;
        this.channelSettings = channelSettings;
    }

    @Override
    public String toString() {
        return String.format("Image %s of channels %s", getName(), getChannelSettings());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof ImageSettings imageSettings))
            return false;
        return imageSettings.getName().equals(getName()) &&
                imageSettings.getChannelSettings().equals(getChannelSettings());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getChannelSettings());
    }

    /**
     * @return the name of the image, or an empty String if not found
     */
    public String getName() {
        if (name == null) {
            name = meta == null || meta.name == null ? "" : meta.name;
        }
        return name;
    }

    /**
     * @return the channel settings of the image, or an empty list if not found
     */
    public List<ChannelSettings> getChannelSettings() {
        if (channelSettings == null) {
            if (channels == null) {
                channelSettings = List.of();
            } else {
                channelSettings = channels.stream()
                        .map(channel -> {
                            int channelColor = 0;
                            try {
                                channelColor = Integer.parseInt(channel.color, 16);
                            } catch (NumberFormatException e) {
                                logger.warn(String.format("Could not convert channel color %s to Integer", channel.color), e);
                            }

                            return new ChannelSettings(
                                    channel.name == null ? "" : channel.name,
                                    channel.window == null ? 0 : channel.window.start,
                                    channel.window == null ? 0 : channel.window.end,
                                    channel.color == null ? 0 : channelColor
                            );
                        })
                        .toList();
            }
        }
        return channelSettings;
    }

    private static class Meta {
        @SerializedName(value = "imageName") private String name;
    }

    private static class Channel {
        @SerializedName(value = "label") private String name;
        @SerializedName(value = "color") private String color;
        @SerializedName(value = "window") private Window window;

        private static class Window {

            @SerializedName(value = "start") private double start;
            @SerializedName(value = "end") private double end;
        }
    }
}
