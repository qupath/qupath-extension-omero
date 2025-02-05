package qupath.ext.omero.core.entities.annotations;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.annotations.annotationsentities.Experimenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An annotation group represents a set of {@link Annotation annotations}
 * attached to an OMERO entity.
 */
public class AnnotationGroup {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationGroup.class);
    private final Map<Class<? extends Annotation>, List<Annotation>> annotations = new HashMap<>();

    /**
     * Creates an annotation group from a JSON object.
     *
     * @param json the JSON supposed to contain the annotation group.
     */
    public AnnotationGroup(JsonObject json) {
        createAnnotations(json, createExperimenters(json));
    }

    @Override
    public String toString() {
        return String.format("Annotation group containing %s", annotations);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof AnnotationGroup annotationGroup))
            return false;
        return annotationGroup.annotations.equals(annotations);
    }

    @Override
    public int hashCode() {
        return annotations.hashCode();
    }

    /**
     * Returns all annotations contained in this annotation group.
     * They are organized by type of annotation (e.g. all comment annotations form one group,
     * all file annotations form another group, etc.).
     *
     * @return the annotations of this annotation group
     */
    public Map<Class<? extends Annotation>, List<Annotation>> getAnnotations() {
        return annotations;
    }

    /**
     * Return all annotations belonging to the provided class.
     *
     * @param annotationClass the class of the annotations to retrieve
     * @return all annotations belonging to the provided class
     * @param <T> the type of annotation to retrieve
     */
    public <T extends Annotation> List<T> getAnnotationsOfClass(Class<T> annotationClass) {
        return annotations.getOrDefault(annotationClass, List.of()).stream()
                .filter(annotationClass::isInstance)
                .map(annotationClass::cast)
                .toList();
    }

    private void createAnnotations(JsonObject json, List<Experimenter> experimenters) {
        Gson gson = new GsonBuilder().registerTypeAdapter(Annotation.class, new Annotation.GsonOmeroAnnotationDeserializer())
                .setStrictness(Strictness.LENIENT)
                .create();
        JsonElement annotationsJSON = json.get("annotations");

        if (annotationsJSON != null && annotationsJSON.isJsonArray()) {
            JsonArray annotationsArray = annotationsJSON.getAsJsonArray();

            for (JsonElement jsonAnnotation: annotationsArray) {
                Annotation annotation = null;
                try {
                    annotation = gson.fromJson(jsonAnnotation, Annotation.class);
                } catch (JsonSyntaxException e) {
                    logger.warn("Error when reading {}", jsonAnnotation, e);
                }

                if (annotation != null) {
                    annotation.updateAdderAndOwner(experimenters);

                    if (annotations.containsKey(annotation.getClass())) {
                        annotations.get(annotation.getClass()).add(annotation);
                    } else {
                        List<Annotation> annotationsForClass = new ArrayList<>();
                        annotationsForClass.add(annotation);
                        annotations.put(annotation.getClass(), annotationsForClass);
                    }
                }
            }
        }
    }

    private static List<Experimenter> createExperimenters(JsonObject json) {
        Gson gson = new GsonBuilder().registerTypeAdapter(Annotation.class, new Annotation.GsonOmeroAnnotationDeserializer())
                .setStrictness(Strictness.LENIENT)
                .create();

        List<Experimenter> experimenters = new ArrayList<>();

        JsonElement experimentersJSON = json.get("experimenters");
        if (experimentersJSON != null && experimentersJSON.isJsonArray()) {
            JsonArray experimentersArray = experimentersJSON.getAsJsonArray();

            for (var jsonExperimenter: experimentersArray) {
                Experimenter experimenter = null;
                try {
                    experimenter = gson.fromJson(jsonExperimenter, Experimenter.class);
                } catch (JsonSyntaxException e) {
                    logger.warn("Error when reading {}", jsonExperimenter, e);
                }

                if (experimenter != null) {
                    experimenters.add(experimenter);
                }
            }
        }

        return experimenters;
    }
}
