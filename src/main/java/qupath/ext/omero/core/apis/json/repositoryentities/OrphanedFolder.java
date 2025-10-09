package qupath.ext.omero.core.apis.json.repositoryentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.ApisHandler;

import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * An orphaned folder is a container for orphaned images (which are described in
 * {@link qupath.ext.omero.core.entities.repositoryentities.serverentities}).
 */
public class OrphanedFolder implements RepositoryEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private final ApisHandler apisHandler;

    /**
     * Create a new orphaned folder.
     *
     * @param apisHandler the APIs handler to use when making requests
     * @throws NullPointerException if the provided parameter is null
     */
    public OrphanedFolder(ApisHandler apisHandler) {
        this.apisHandler = Objects.requireNonNull(apisHandler);
    }

    @Override
    public boolean hasChildren() {
        // It is not possible to know if the orphaned folder has children without sending a request
        return true;
    }

    @Override
    public CompletableFuture<? extends List<? extends RepositoryEntity>> getChildren(long ownerId, long groupId) {
        return apisHandler.getOrphanedImages(ownerId, groupId);
    }

    @Override
    public String getLabel() {
        return resources.getString("Entities.RepositoryEntities.OrphanedFolder.orphanedImages");
    }

    @Override
    public String toString() {
        return String.format("Orphaned folder of %s", apisHandler.getWebServerUri());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof OrphanedFolder orphanedFolder))
            return false;
        return Objects.equals(orphanedFolder.apisHandler, apisHandler);
    }

    @Override
    public int hashCode() {
        return apisHandler.hashCode();
    }
}
