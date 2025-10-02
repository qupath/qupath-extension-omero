package qupath.ext.omero.core.apis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.SimpleServerEntity;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class to find a {@link SimpleServerEntity} in an OMERO URI.
 */
class EntityParser {

    private static final Logger logger = LoggerFactory.getLogger(EntityParser.class);
    private static final Map<Pattern, Function<Long, SimpleServerEntity>> SIMPLE_ENTITY_FROM_URI_CREATOR = Map.of(
            Pattern.compile("/webclient/\\?show=project-(\\d+)"), id -> new SimpleServerEntity(SimpleServerEntity.EntityType.PROJECT, id),
            Pattern.compile("/webclient/\\?show=dataset-(\\d+)"), id -> new SimpleServerEntity(SimpleServerEntity.EntityType.DATASET, id),
            Pattern.compile("/webclient/\\?show=image-(\\d+)"), id -> new SimpleServerEntity(SimpleServerEntity.EntityType.IMAGE, id),
            Pattern.compile("/webclient/img_detail/(\\d+)"), id -> new SimpleServerEntity(SimpleServerEntity.EntityType.IMAGE, id),
            Pattern.compile("/webgateway/img_detail/(\\d+)"), id -> new SimpleServerEntity(SimpleServerEntity.EntityType.IMAGE, id),
            Pattern.compile("/iviewer/\\?images=(\\d+)"), id -> new SimpleServerEntity(SimpleServerEntity.EntityType.IMAGE, id),
            Pattern.compile("/webclient/\\?show=well-(\\d+)"), id -> new SimpleServerEntity(SimpleServerEntity.EntityType.WELL, id),
            Pattern.compile("/webclient/\\?show=run-(\\d+)"), id -> new SimpleServerEntity(SimpleServerEntity.EntityType.PLATE_ACQUISITION, id),
            Pattern.compile("/webclient/\\?show=plate-(\\d+)"), id -> new SimpleServerEntity(SimpleServerEntity.EntityType.PLATE, id),
            Pattern.compile("/webclient/\\?show=screen-(\\d+)"), id -> new SimpleServerEntity(SimpleServerEntity.EntityType.SCREEN, id)
    );

    private EntityParser() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Attempt to find a {@link SimpleServerEntity} in the provided URI.
     *
     * @param uri the URI containing the {@link SimpleServerEntity} to find
     * @return the entity if it was found, or an empty optional otherwise
     */
    public static Optional<SimpleServerEntity> parseUri(URI uri) {
        logger.debug("Finding entity in {}...", uri);

        String entityUrl = URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);

        for (var entry: SIMPLE_ENTITY_FROM_URI_CREATOR.entrySet()) {
            Matcher matcher = entry.getKey().matcher(entityUrl);

            if (matcher.find()) {
                String idValue = matcher.group(1);
                try {
                    SimpleServerEntity serverEntity = entry.getValue().apply(Long.parseLong(idValue));
                    logger.debug(
                            "Found {} with ID {} in {} with matcher {}",
                            serverEntity.entityType(),
                            serverEntity.id(),
                            entityUrl,
                            matcher
                    );
                    return Optional.of(serverEntity);
                } catch (NumberFormatException e) {
                    logger.debug("Found entity ID {} in {} with matcher {} but it is not an integer. Skipping it", idValue, entityUrl, matcher, e);
                }
            } else {
                logger.debug("Entity not found in {} with matcher {}", entityUrl, matcher);
            }
        }

        logger.debug("Entity not found in {}", entityUrl);
        return Optional.empty();
    }
}
