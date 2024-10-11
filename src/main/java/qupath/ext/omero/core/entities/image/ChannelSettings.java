package qupath.ext.omero.core.entities.image;

import qupath.lib.common.ColorTools;

import java.util.Objects;

/**
 * Represents several settings about a channel.
 */
public class ChannelSettings {

    private final String name;
    private final double minDisplayRange;
    private final double maxDisplayRange;
    private final int rgbColor;

    /**
     * Create a new channel settings
     *
     * @param name the name of the channel
     * @param minDisplayRange the minimum table lookup value for this channel
     * @param maxDisplayRange the maximum table lookup value for this channel
     * @param rgbColor the RGB color of this channel as a packed (A)RGB integer
     */
    public ChannelSettings(String name, double minDisplayRange, double maxDisplayRange, int rgbColor) {
        this.name = name;
        this.minDisplayRange = minDisplayRange;
        this.maxDisplayRange = maxDisplayRange;
        this.rgbColor = rgbColor;
    }

    /**
     * Create a new channel settings
     *
     * @param minDisplayRange the minimum table lookup value for this channel
     * @param maxDisplayRange the maximum table lookup value for this channel
     * @param rgbColor the RGB color of this channel as a packed (A)RGB integer
     */
    public ChannelSettings(double minDisplayRange, double maxDisplayRange, int rgbColor) {
        this("", minDisplayRange, maxDisplayRange, rgbColor);
    }

    /**
     * Create a new channel settings
     *
     * @param minDisplayRange the minimum table lookup value for this channel
     * @param maxDisplayRange the maximum table lookup value for this channel
     */
    public ChannelSettings(double minDisplayRange, double maxDisplayRange) {
        this("", minDisplayRange, maxDisplayRange, 0);
    }

    /**
     * Create a new channel settings
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

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof ChannelSettings channelSettings))
            return false;
        return channelSettings.name.equals(name) &&
                channelSettings.minDisplayRange == minDisplayRange &&
                channelSettings.maxDisplayRange == maxDisplayRange &&
                channelSettings.rgbColor == rgbColor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, minDisplayRange, maxDisplayRange, rgbColor);
    }

    /**
     * @return the name of this channel
     */
    public String getName() {
        return name;
    }

    /**
     * @return the minimum table lookup value for this channel
     */
    public double getMinDisplayRange() {
        return minDisplayRange;
    }

    /**
     * @return the maximum table lookup value for this channel
     */
    public double getMaxDisplayRange() {
        return maxDisplayRange;
    }

    /**
     * @return the RGB color of this channel as a packed (A)RGB integer
     */
    public int getRgbColor() {
        return rgbColor;
    }
}
