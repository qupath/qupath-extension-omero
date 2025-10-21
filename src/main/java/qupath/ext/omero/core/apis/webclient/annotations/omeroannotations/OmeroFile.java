package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import java.util.Objects;

/**
 * Represents information on a file stored alongside an OMERO entity.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param name the name of the file. Required
 * @param mimetype the Multipurpose Internet Mail Extensions type of the file. Required
 * @param size the size of the file (in bytes). Required
 */
public record OmeroFile(
        String name,
        String mimetype,
        Long size
) {
    public OmeroFile {
        Objects.requireNonNull(name, "name not provided");
        Objects.requireNonNull(mimetype, "mimetype not provided");
        Objects.requireNonNull(size, "size not provided");
    }
}
