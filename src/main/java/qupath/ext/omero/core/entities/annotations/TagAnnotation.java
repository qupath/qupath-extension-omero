package qupath.ext.omero.core.entities.annotations;

import qupath.ext.omero.Utils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Annotation containing a text tag.
 */
public class TagAnnotation extends Annotation {

    private static final ResourceBundle resources = Utils.getResources();
    private static final List<String> ACCEPTED_TYPES = List.of("TagAnnotationI", "tag");
    private String textValue;

    @Override
    public String toString() {
        return String.format("%s. Value: %s", super.toString(), textValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof TagAnnotation tagAnnotation))
            return false;
        return Objects.equals(tagAnnotation.textValue, textValue);
    }

    @Override
    public int hashCode() {
        return (textValue == null ? "" : textValue).hashCode();
    }

    /**
     * @return a localized title for a tag annotation
     */
    public static String getTitle() {
        return resources.getString("Entities.Annotation.Tag.title");
    }

    /**
     * Indicates if an annotation type refers to a tag annotation.
     *
     * @param type the annotation type
     * @return whether this annotation type refers to a tag annotation
     */
    public static boolean isOfType(String type) {
        return ACCEPTED_TYPES.stream().anyMatch(type::equalsIgnoreCase);
    }

    /**
     * @return the actual tag of the annotation, or an empty Optional if not found
     */
    public Optional<String> getValue() {
        return Optional.ofNullable(textValue);
    }
}
