package qupath.ext.omero.core.entities.annotations;

import qupath.ext.omero.Utils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Annotation containing a text comment.
 */
public class CommentAnnotation extends Annotation {

    private static final ResourceBundle resources = Utils.getResources();
    private static final List<String> ACCEPTED_TYPES = List.of("CommentAnnotationI", "comment");
    private String textValue;

    @Override
    public String toString() {
        return String.format("%s. Value: %s", super.toString(), textValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof CommentAnnotation commentAnnotation))
            return false;
        return Objects.equals(commentAnnotation.textValue, textValue);
    }

    @Override
    public int hashCode() {
        return (textValue == null ? "" : textValue).hashCode();
    }

    /**
     * @return a localized title for a comment annotation
     */
    public static String getTitle() {
        return resources.getString("Entities.Annotation.Comment.title");
    }

    /**
     * Indicates if an annotation type refers to a comment annotation.
     *
     * @param type the annotation type
     * @return whether this annotation type refers to a comment annotation
     */
    public static boolean isOfType(String type) {
        return ACCEPTED_TYPES.stream().anyMatch(type::equalsIgnoreCase);
    }

    /**
     * @return the actual comment of the annotation, or an empty Optional if not found
     */
    public Optional<String> getValue() {
        return Optional.ofNullable(textValue);
    }
}
