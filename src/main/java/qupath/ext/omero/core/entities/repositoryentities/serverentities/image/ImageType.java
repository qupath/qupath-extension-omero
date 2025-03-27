package qupath.ext.omero.core.entities.repositoryentities.serverentities.image;

/**
 * This class contains the format the pixel values of an image use.
 * For example, uint8 (for 8-bit images).
 *
 * @param value the format the pixel values use (can be null if not found)
 */
record ImageType(String value) {}
