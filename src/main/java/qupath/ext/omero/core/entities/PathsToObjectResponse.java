package qupath.ext.omero.core.entities;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Well;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A class to parse the response of a paths to object query (usually from
 * {@code <omero_web_address>/webclient/api/paths_to_object/?image=<image_id>}).
 */
public class PathsToObjectResponse {

    private static final Logger logger = LoggerFactory.getLogger(PathsToObjectResponse.class);
    private static final Map<String, Function<Long, ServerEntity>> ENTITY_TO_SERVER_ENTITY = Map.of(
            "screen", Screen::new,
            "plate", Plate::new,
            "acquisition", PlateAcquisition::new,
            "well", Well::new,
            "project", Project::new,
            "dataset", Dataset::new
    );
    private static final Gson gson = new Gson();
    private record Entity(String type, long id) {}
    private record Paths(List<List<Entity>> paths) {}

    private PathsToObjectResponse() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Attempt to parse the server entities present in the provided response. Only {@link Screen}, {@link Plate},
     * {@link PlateAcquisition}, {@link Well}, {@link Project}, and {@link Dataset} entities are considered.
     *
     * @param response the raw HTTP response that should contain the server entities
     * @return the server entities contained in the provided response
     * @throws com.google.gson.JsonSyntaxException if the provided response is not a valid representation
     * of the expected response
     * @throws NullPointerException if the provided response is null, empty, or represents a JSON object that
     * doesn't contain the `paths` field
     */
    public static List<ServerEntity> getServerEntitiesFromResponse(String response) {
        logger.debug("Parsing server entities from {}", response);

        return gson.fromJson(response, Paths.class).paths().stream()
                .flatMap(list -> list.stream()
                        .map(entity -> {
                            if (entity.type() != null && ENTITY_TO_SERVER_ENTITY.containsKey(entity.type())) {
                                ServerEntity serverEntity = ENTITY_TO_SERVER_ENTITY.get(entity.type()).apply(entity.id());
                                logger.debug("Entity {} identified to {}", entity, serverEntity);
                                return serverEntity;
                            } else {
                                logger.debug("Entity {} not identified. Skipping it", entity);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                )
                .toList();
    }
}
