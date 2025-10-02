package qupath.ext.omero.core.entities;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A class to parse the response of a paths to object query (usually from
 * {@code <omero_web_address>/webclient/api/paths_to_object/?image=<image_id>}).
 *
 * @param entityType the type of the entity
 * @param id the ID of the entity
 */
public record SimpleServerEntity(EntityType entityType, long id) {

    private static final Logger logger = LoggerFactory.getLogger(SimpleServerEntity.class);
    private static final Gson gson = new Gson();
    /**
     * An entity type.
     */
    public enum EntityType {
        /**
         * A screen.
         */
        SCREEN("screen"),
        /**
         * A plate.
         */
        PLATE("plate"),
        /**
         * A plate acquisition.
         */
        PLATE_ACQUISITION("acquisition"),
        /**
         * A well.
         */
        WELL("well"),
        /**
         * A project.
         */
        PROJECT("project"),
        /**
         * A dataset.
         */
        DATASET("dataset"),
        /**
         * An image.
         */
        IMAGE("image");

        private final String apiName;

        EntityType(String apiName) {
            this.apiName = apiName;
        }
    }
    private record Paths(List<List<Entity>> paths) {}
    private record Entity(String type, Long id) {}

    /**
     * Attempt to parse the server entities present in the provided JSON. Only entity types of {@link EntityType} are considered.
     *
     * @param json a JSON element containing the response to parse
     * @return a list of server entities present in the provided JSON element
     * @throws com.google.gson.JsonSyntaxException if the provided JSON is not a valid representation of the expected JSON
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

                            Optional<EntityType> foundEntityType = Arrays.stream(EntityType.values())
                                    .filter(entityType -> entityType.apiName.equals(entity.type))
                                    .findAny();

                            if (foundEntityType.isPresent()) {
                                logger.debug("Entity {} identified to {}", entity, foundEntityType.get());
                                return new SimpleServerEntity(foundEntityType.get(), entity.id());
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
