package qupath.ext.omero.core.apis.commonentities;

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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A class that represents the type and ID of a {@link ServerEntity}.
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
     * Create an instance of this class from a {@link ServerEntity}.
     *
     * @param serverEntity the server entity to use when creating this simple server entity
     */
    public SimpleServerEntity(ServerEntity serverEntity) {
        this(
                switch (serverEntity) {
                    case Dataset ignored -> EntityType.DATASET;
                    case Image image -> EntityType.IMAGE;
                    case Plate plate -> EntityType.PLATE;
                    case PlateAcquisition plateAcquisition -> EntityType.PLATE_ACQUISITION;
                    case Project project -> EntityType.PROJECT;
                    case Screen screen -> EntityType.SCREEN;
                    case Well well -> EntityType.WELL;
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
