package qupath.ext.omero.core.apis.webclient.annotations;

import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroCommentAnnotation;

import java.util.List;

/**
 * Annotation containing a text comment.
 */
public class CommentAnnotation extends Annotation {

    private final String comment;

    /**
     * Create the annotation.
     *
     * @param omeroCommentAnnotation the OMERO annotation to create this annotation from
     * @param experimenters a list of experimenters that may contain the adder and owner of this annotation
     * @throws NullPointerException if one of the provided parameters is null
     */
    public CommentAnnotation(OmeroCommentAnnotation omeroCommentAnnotation, List<OmeroSimpleExperimenter> experimenters) {
        super(
                omeroCommentAnnotation.id(),
                omeroCommentAnnotation.namespace(),
                omeroCommentAnnotation.link() == null ? null : omeroCommentAnnotation.link().owner(),
                omeroCommentAnnotation.owner(),
                experimenters
        );

        this.comment = omeroCommentAnnotation.comment();
    }

    @Override
    public String toString() {
        return String.format("Comment annotation %d: %s", getId(), comment);
    }

    /**
     * @return the comment value of this annotation
     */
    public String getComment() {
        return comment;
    }
}
