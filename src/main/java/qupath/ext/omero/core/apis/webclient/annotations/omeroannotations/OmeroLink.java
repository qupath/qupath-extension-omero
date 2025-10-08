package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import java.util.Objects;

/**
 * Represents an OMERO link between an annotation and an experimenter.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param owner the adder of the annotation. Required
 */
public record OmeroLink(
        OmeroAnnotationExperimenter owner
) {
    public OmeroLink {
        Objects.requireNonNull(owner);
    }
}
