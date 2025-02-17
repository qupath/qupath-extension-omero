package qupath.ext.omero.core.entities.annotations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Annotation containing information on a file attached to an OMERO entity.
 */
public class FileAnnotation extends Annotation {

    private static final ResourceBundle resources = Utils.getResources();
    private static final Logger logger = LoggerFactory.getLogger(FileAnnotation.class);
    private static final List<String> ACCEPTED_TYPES = List.of("FileAnnotationI", "file");
    private Map<String, String> file;

    @Override
    public String toString() {
        return String.format("%s. Map: %s", super.toString(), file);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof FileAnnotation fileAnnotation))
            return false;
        return Objects.equals(fileAnnotation.file, file);
    }

    @Override
    public int hashCode() {
        return (file == null ? "" : file).hashCode();
    }

    /**
     * @return a localized title for a file annotation
     */
    public static String getTitle() {
        return resources.getString("Entities.Annotation.File.title");
    }

    /**
     * Indicates if an annotation type refers to a file annotation.
     *
     * @param type the annotation type
     * @return whether this annotation type refers to a file annotation
     */
    public static boolean isOfType(String type) {
        return ACCEPTED_TYPES.stream().anyMatch(type::equalsIgnoreCase);
    }

    /**
     * @return the name of the attached file, or an empty Optional if it was not found
     */
    public Optional<String> getFilename() {
        if (file == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(file.get("name"));
        }
    }

    /**
     * @return the MIME type of the attached file, or an empty Optional if it was not found
     */
    public Optional<String> getMimeType() {
        if (file == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(file.get("mimetype"));
        }
    }

    /**
     * @return the size of the attached file in bytes, or an empty Optional if it was not found
     */
    public Optional<Long> getFileSize() {
        if (file == null || file.get("size") == null) {
            return Optional.empty();
        } else {
            String size = file.get("size");
            try {
                return Optional.of(Long.parseLong(size));
            } catch (NumberFormatException e) {
                logger.warn("Cannot convert {} to a number in file annotation", size, e);
                return Optional.empty();
            }
        }
    }
}
