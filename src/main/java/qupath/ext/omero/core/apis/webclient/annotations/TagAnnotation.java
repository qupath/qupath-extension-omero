package qupath.ext.omero.core.apis.webclient.annotations;

import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroTagAnnotation;

import java.util.List;

/**
 * Annotation containing a text tag.
 */
public class TagAnnotation extends Annotation {

    private final String tag;

    /**
     * Create the annotation.
     *
     * @param omeroTagAnnotation the OMERO annotation to create this annotation from
     * @param experimenters a list of experimenters that should contain the adder and owner of this annotation
     * @throws NullPointerException if one of the provided parameters is null
     */
    public TagAnnotation(OmeroTagAnnotation omeroTagAnnotation, List<OmeroSimpleExperimenter> experimenters) {
        super(
                omeroTagAnnotation.id(),
                omeroTagAnnotation.namespace(),
                omeroTagAnnotation.link() == null ? null : omeroTagAnnotation.link().owner(),
                omeroTagAnnotation.owner(),
                experimenters
        );

        this.tag = omeroTagAnnotation.tag();
    }

    @Override
    public String toString() {
        return String.format("Tag annotation %d containing the tag %s", getId(), tag);
    }

    /**
     * @return the tag of this annotation
     */
    public String getTag() {
        return tag;
    }
}
