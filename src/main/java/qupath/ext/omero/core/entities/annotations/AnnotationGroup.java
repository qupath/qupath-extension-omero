package qupath.ext.omero.core.entities.annotations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.annotations.annotationsentities.Experimenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An annotation group represents a set of {@link Annotation annotations} attached to an OMERO entity.
 *
 * @param annotations all annotations contained in this annotation group. They are organized by type of
 *                    annotation (e.g. all comment annotations form one group, all file annotations form
 *                    another group, etc.).
 */
public record AnnotationGroup(Map<Class<? extends Annotation>, List<Annotation>> annotations) {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationGroup.class);
    private static final Gson gson = new GsonBuilder().registerTypeAdapter(Annotation.class, new Annotation.GsonOmeroAnnotationDeserializer())
            .create();

    /**
     * Creates an annotation group from a JSON object.
     *
     * @param json the JSON supposed to contain the annotation group
     * @throws IllegalArgumentException if the provided JSON doesn't contain the required elements
     * @throws com.google.gson.JsonSyntaxException if the provided JSON contains unexpected representations of annotations
     */
    public AnnotationGroup(JsonObject json) {
        this(createAnnotations(json, createExperimenters(json)));
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

    private static List<Experimenter> createExperimenters(JsonObject json) {
        if (!json.has("experimenters") || !json.get("experimenters").isJsonArray()) {
            logger.debug("'experimenters' array not found in {}", json);
            return List.of();
        }
        JsonArray jsonExperimenters = json.get("experimenters").getAsJsonArray();

        List<Experimenter> experimenters = new ArrayList<>();
        for (JsonElement jsonExperimenter: jsonExperimenters) {
            Experimenter experimenter = gson.fromJson(jsonExperimenter, Experimenter.class);

            if (experimenter != null) {
                experimenters.add(experimenter);
            }
        }

        logger.debug("Found experimenters {} in annotation group", experimenters);

        return experimenters;
    }

    private static Map<Class<? extends Annotation>, List<Annotation>> createAnnotations(JsonObject json, List<Experimenter> experimenters) {
        if (!json.has("annotations") || !json.get("annotations").isJsonArray()) {
            logger.debug("'annotations' array not found in {}", json);
            return Map.of();
        }
        JsonArray jsonAnnotations = json.get("annotations").getAsJsonArray();

        Map<Class<? extends Annotation>, List<Annotation>> annotations = new HashMap<>();
        for (JsonElement jsonAnnotation: jsonAnnotations) {
            Annotation annotation = gson.fromJson(jsonAnnotation, Annotation.class);

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

        logger.debug("Found annotations {} in annotation group", annotations);

        return annotations;
    }
}
