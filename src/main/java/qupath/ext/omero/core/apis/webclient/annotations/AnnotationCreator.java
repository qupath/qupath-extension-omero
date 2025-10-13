package qupath.ext.omero.core.apis.webclient.annotations;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroCommentAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroFileAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroMapAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroRatingAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroTagAnnotation;

import java.util.List;
import java.util.Objects;

/**
 * A class to create {@link Annotation} from a JSON response to a request like <omero_server>/webclient/api/annotations/?image=<image_id>.
 */
public class AnnotationCreator {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationCreator.class);
    private static final Gson gson = new Gson();

    private AnnotationCreator() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Create a list of annotations from the provided JSON object.
     *
     * @param json a JSON object containing a list of annotations
     * @return a list of annotations corresponding to the provided JSON object
     * @throws RuntimeException if the provided JSON object is null or has an unexpected format
     */
    public static List<Annotation> createAnnotations(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException(String.format("The provided JSON %s is not a JSON object", json));
        }
        JsonObject jsonObject = json.getAsJsonObject();

        if (!jsonObject.has("annotations") || !jsonObject.get("annotations").isJsonArray()) {
            throw new IllegalArgumentException(String.format("'annotations' array not found in the provided JSON %s", jsonObject));
        }
        if (!jsonObject.has("experimenters") || !jsonObject.get("experimenters").isJsonArray()) {
            throw new IllegalArgumentException(String.format("'experimenters' array not found in the provided JSON %s", jsonObject));
        }

        List<OmeroSimpleExperimenter> experimenters = createExperimenters(jsonObject.getAsJsonArray("experimenters"));

        logger.debug("Creating annotations from {}", jsonObject.getAsJsonArray("annotations"));
        return jsonObject.getAsJsonArray("annotations").asList()
                .stream()
                .map(annotation -> {
                    if (!annotation.isJsonObject()) {
                        logger.warn("{} is not a JSON object. Skipping it", annotation);
                        return null;
                    }
                    JsonObject annotationObject = annotation.getAsJsonObject();

                    if (!annotationObject.has("class") || annotationObject.get("class").isJsonPrimitive()) {
                        logger.warn("'class' attribute not found in {}. Skipping it", annotationObject);
                        return null;
                    }
                    String annotationClass = annotationObject.get("class").getAsString();

                    return switch (annotationClass) {
                        case OmeroMapAnnotation.TYPE -> new MapAnnotation(gson.fromJson(annotationObject, OmeroMapAnnotation.class), experimenters);
                        case OmeroTagAnnotation.TYPE -> new TagAnnotation(gson.fromJson(annotationObject, OmeroTagAnnotation.class), experimenters);
                        case OmeroCommentAnnotation.TYPE -> new CommentAnnotation(gson.fromJson(annotationObject, OmeroCommentAnnotation.class), experimenters);
                        case OmeroFileAnnotation.TYPE -> new FileAnnotation(gson.fromJson(annotationObject, OmeroFileAnnotation.class), experimenters);
                        case OmeroRatingAnnotation.TYPE -> new RatingAnnotation(gson.fromJson(annotationObject, OmeroRatingAnnotation.class), experimenters);
                        default -> {
                            logger.warn("Annotation class {} not recognized. Skipping {}", annotationClass, annotation);
                            yield null;
                        }
                    };
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<OmeroSimpleExperimenter> createExperimenters(JsonArray experimentersJson) {
        logger.debug("Creating simple experimenters from {}", experimentersJson);

        List<OmeroSimpleExperimenter> experimenters = experimentersJson.asList()
                .stream()
                .map(experimenter -> {
                    try {
                        return gson.fromJson(experimenter, OmeroSimpleExperimenter.class);
                    } catch (RuntimeException e) {
                        logger.warn("Cannot create simple experimenter from {}. Skipping it", experimenter, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        logger.debug("Created simple experimenters {} from {}", experimenters, experimentersJson);

        return experimenters;
    }
}
