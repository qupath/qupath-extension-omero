package qupath.ext.omero.core.entities.repositoryentities2;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * An element belonging to the OMERO entity hierarchy.
 */
public interface RepositoryEntity {

    /**
     * @return whether this entity has children
     */
    boolean hasChildren();

    /**
     * Returns the list of children of this element belonging to the provided owner and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     * if the request failed for example).
     *
     * @param ownerId the ID of the owner that should own the entities to retrieve
     * @param groupId the ID of the group that should own the entities to retrieve
     * @return a CompletableFuture (that may complete exceptionally) with the list of children of this entity
     */
    CompletableFuture<? extends List<? extends RepositoryEntity>> getChildren(long ownerId, long groupId);

    /**
     * @return a localizable text describing the entity
     */
    String getLabel();
}