package qupath.ext.omero.core.apis.iviewer.imageentities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.commonentities.ChannelSettings;

import java.util.List;
import java.util.Objects;

/**
 * Represent some information about an image.
 */
public class ImageData {

    private static final Logger logger = LoggerFactory.getLogger(ImageData.class);
    private final String name;
    private final List<ChannelSettings> channelSettings;

    /**
     * Create the image data.
     *
     * @param omeroImageData the OMERO image data to create this image data from
     */
    public ImageData(OmeroImageData omeroImageData) {
        this.name = omeroImageData.metadata() == null || omeroImageData.metadata().imageName() == null ? "" : omeroImageData.metadata().imageName();

        this.channelSettings = omeroImageData.channels() == null ? List.of() : omeroImageData.channels().stream()
                .map(channel -> {
                    int channelColor = 0;
                    if (channel.color() == null) {
                        logger.debug("No channel color in {}. Considering it black", channel);
                    } else {
                        try {
                            channelColor = Integer.parseInt(channel.color(), 16);
                        } catch (NumberFormatException e) {
                            logger.warn("Could not convert channel color {} to int. Considering it black", channel.color(), e);
                        }
                    }

                    return new ChannelSettings(
                            channel.label() == null ? "" : channel.label(),
                            channel.window() == null || channel.window().start() == null ? 0 : channel.window().start(),
                            channel.window() == null || channel.window().end() == null ? 0 : channel.window().end(),
                            channelColor
                    );
                })
                .toList();
    }

    @Override
    public String toString() {
        return String.format("Image %s of channels %s", getName(), getChannelSettings());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof ImageData imageData))
            return false;
        return imageData.getName().equals(getName()) &&
                imageData.getChannelSettings().equals(getChannelSettings());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getChannelSettings());
    }

    /**
     * @return the name of the image, or an empty String if not found
     */
    public String getName() {
        return name;
    }

    /**
     * @return the channel settings of the image, or an empty list if not found
     */
    public List<ChannelSettings> getChannelSettings() {
        return channelSettings;
    }
}
