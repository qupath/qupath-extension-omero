package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import java.util.Objects;

/**
 * Represents an OMERO experimenter owning an annotation.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param id the ID of the experimenter. Required
 */
public record OmeroAnnotationExperimenter(
        Long id
) {
    public OmeroAnnotationExperimenter {
        Objects.requireNonNull(id, "id not provided");
    }
}
