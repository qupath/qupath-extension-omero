package qupath.ext.omero.core.apis.webclient;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Image;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Well;

import java.util.List;
import java.util.Objects;

/**
 * A class that represents the type and ID of a {@link ServerEntity}.
 *
 * @param entityType the type of the entity
 * @param id the ID of the entity
 */
public record SimpleServerEntity(EntityType entityType, long id) {

    private static final Logger logger = LoggerFactory.getLogger(SimpleServerEntity.class);
    private static final Gson gson = new Gson();
    private record Paths(List<List<Entity>> paths) {}
    private record Entity(String type, Long id) {}

    /**
     * Create an instance of this class from a {@link ServerEntity}.
     *
     * @param serverEntity the server entity to use when creating this simple server entity
     * @throws IllegalArgumentException if the provided server entity does not correspond to an {@link EntityType}
     */
    public SimpleServerEntity(ServerEntity serverEntity) {
        this(
                switch (serverEntity) {
                    case Dataset ignored -> EntityType.DATASET;
                    case Image ignored -> EntityType.IMAGE;
                    case Plate ignored -> EntityType.PLATE;
                    case PlateAcquisition ignored -> EntityType.PLATE_ACQUISITION;
                    case Project ignored -> EntityType.PROJECT;
                    case Screen ignored -> EntityType.SCREEN;
                    case Well ignored -> EntityType.WELL;
                    default -> throw new IllegalStateException(String.format(
                            "Unexpected server entity %s. Cannot create simple server entity",
                            serverEntity.getClass()
                    ));
                },
                serverEntity.getId()
        );
    }

    /**
     * Attempt to parse the server entities present in the provided JSON. Only entity types of {@link EntityType} are considered.
     *
     * @param json a JSON element containing the response to parse
     * @return a list of server entities present in the provided JSON element
     * @throws JsonSyntaxException if the provided JSON is not a valid representation of the expected JSON
     * @throws IllegalArgumentException if the provided JSON has an unexpected format
     */
    public static List<SimpleServerEntity> createFromJson(JsonElement json) {
        Paths paths = gson.fromJson(json, Paths.class);
        if (paths == null || paths.paths == null) {
            throw new IllegalArgumentException(String.format("Unexpected JSON %s; cannot convert it to a list of server entities", json));
        }

        return paths.paths().stream()
                .flatMap(list -> list.stream()
                        .map(entity -> {
                            if (entity.type == null || entity.id == null) {
                                logger.debug("Entity {} not identified. Skipping it", entity);
                                return null;
                            }

                            EntityType entityType = switch (entity.type) {
                                case "screen" -> EntityType.SCREEN;
                                case "plate" -> EntityType.PLATE;
                                case "acquisition" -> EntityType.PLATE_ACQUISITION;
                                case "well" -> EntityType.WELL;
                                case "project" -> EntityType.PROJECT;
                                case "dataset" -> EntityType.DATASET;
                                case "image" -> EntityType.IMAGE;
                                default -> null;
                            };

                            if (entityType == null) {
                                logger.debug("Entity {} not identified. Skipping it", entity);
                                return null;
                            } else {
                                logger.debug("Entity {} identified to {}", entity, entityType);
                                return new SimpleServerEntity(entityType, entity.id());
                            }
                        })
                        .filter(Objects::nonNull)
                )
                .toList();
    }
}
