package qupath.ext.omero.core.entities.omeroentities.server.image;

import java.util.Objects;

/**
 * Represents the data type each pixel of an OMERO image has.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param value a text describing the image type. Required
 */
record OmeroImageType(String value) {
    public OmeroImageType {
        Objects.requireNonNull(value);
    }
}
