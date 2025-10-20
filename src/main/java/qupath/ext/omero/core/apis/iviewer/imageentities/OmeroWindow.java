package qupath.ext.omero.core.apis.iviewer.imageentities;

/**
 * Represents a range of values.
 *
 * @param start the inclusive lower bound of the values. Optional
 * @param end the inclusive upper bound of the values. Optional
 */
public record OmeroWindow(
        Double start,
        Double end
) {}
