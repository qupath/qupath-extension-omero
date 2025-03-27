package qupath.ext.omero.core.entities.annotations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;

import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Annotation containing several key-value pairs (e.g. license, release date).
 * Note that some keys can be duplicated.
 */
public class MapAnnotation extends Annotation {

    private static final Logger logger = LoggerFactory.getLogger(MapAnnotation.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final List<String> ACCEPTED_TYPES = List.of("MapAnnotationI", "map");
    private List<List<String>> values;
    /**
     * A key-value pair of text.
     *
     * @param key the key of the pair
     * @param value the value of the pair
     */
    public record Pair(String key, String value) {}

    @Override
    public String toString() {
        return String.format("%s. Values: %s", super.toString(), values);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof MapAnnotation mapAnnotation))
            return false;
        return Objects.equals(mapAnnotation.values, values);
    }

    @Override
    public int hashCode() {
        return (values == null ? "" : values).hashCode();
    }

    /**
     * @return a localized title for a map annotation
     */
    public static String getTitle() {
        return resources.getString("Entities.Annotation.Map.title");
    }

    /**
     * Indicates if an annotation type refers to a map annotation.
     *
     * @param type the annotation type
     * @return whether this annotation type refers to a map annotation
     */
    public static boolean isOfType(String type) {
        return ACCEPTED_TYPES.stream().anyMatch(type::equalsIgnoreCase);
    }

    /**
     * @return the values of this annotation as a list of pairs
     */
    public List<Pair> getPairs() {
        return values == null ? List.of() : values.stream()
                .map(value -> {
                    if (value.size() > 1) {
                        return new Pair(value.get(0), value.get(1));
                    } else {
                        logger.debug("The size of {} is less than two. Cannot create entry for map annotation {}", value, this);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
