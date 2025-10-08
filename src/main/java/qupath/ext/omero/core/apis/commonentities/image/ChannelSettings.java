package qupath.ext.omero.core.apis.commonentities.image;

import qupath.lib.common.ColorTools;

/**
 * Represents several settings about a channel.
 *
 * @param name the name of the channel
 * @param minDisplayRange the minimum table lookup value for this channel
 * @param maxDisplayRange the maximum table lookup value for this channel
 * @param rgbColor the RGB color of this channel as a packed (A)RGB integer
 */
public record ChannelSettings(String name, double minDisplayRange, double maxDisplayRange, int rgbColor) {

    /**
     * Create a new channel settings.
     *
     * @param minDisplayRange the minimum table lookup value for this channel
     * @param maxDisplayRange the maximum table lookup value for this channel
     * @param rgbColor the RGB color of this channel as a packed (A)RGB integer
     */
    public ChannelSettings(double minDisplayRange, double maxDisplayRange, int rgbColor) {
        this("", minDisplayRange, maxDisplayRange, rgbColor);
    }

    /**
     * Create a new channel settings.
     *
     * @param minDisplayRange the minimum table lookup value for this channel
     * @param maxDisplayRange the maximum table lookup value for this channel
     */
    public ChannelSettings(double minDisplayRange, double maxDisplayRange) {
        this("", minDisplayRange, maxDisplayRange, 0);
    }

    /**
     * Create a new channel settings.
     *
     * @param name the name of the channel
     */
    public ChannelSettings(String name) {
        this(name, 0, 0, 0);
    }

    @Override
    public String toString() {
        return String.format(
                "Channel %s of color %02X%02X%02X, from %f to %f",
                name,
                ColorTools.unpackRGB(rgbColor)[0],
                ColorTools.unpackRGB(rgbColor)[1],
                ColorTools.unpackRGB(rgbColor)[2],
                minDisplayRange,
                maxDisplayRange
        );
    }
}
