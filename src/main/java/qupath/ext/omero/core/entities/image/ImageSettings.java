package qupath.ext.omero.core.entities.image;

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
    private Meta meta;
    private List<Channel> channels;

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
            name = meta == null || meta.imageName == null ? "" : meta.imageName;
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
                                logger.warn("Could not convert channel color {} to Integer", channel.color, e);
                            }

                            return new ChannelSettings(
                                    channel.label == null ? "" : channel.label,
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
        private String imageName;
    }

    private static class Channel {
        private String label;
        private String color;
        private Window window;

        private static class Window {

            private double start;
            private double end;
        }
    }
}
