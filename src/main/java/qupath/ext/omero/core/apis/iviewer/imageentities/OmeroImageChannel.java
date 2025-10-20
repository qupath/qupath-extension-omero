package qupath.ext.omero.core.apis.iviewer.imageentities;

/**
 * Represents information on the channel of an image.
 *
 * @param label a text describing the channel. Optional
 * @param color a text representing the hexadecimal color of the channel with the RGB format. For example, "FF0000" to represent red. Optional
 * @param window information on the range of values of this channel. Optional
 */
public record OmeroImageChannel(
        String label,
        String color,
        OmeroWindow window
) {}
