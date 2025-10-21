package qupath.ext.omero.core.apis.iviewer.imageentities;

/**
 * Represent a name of an OMERO image.
 *
 * @param imageName the name of the image. Optional
 */
public record OmeroImageMetadata(
        String imageName
) {}
