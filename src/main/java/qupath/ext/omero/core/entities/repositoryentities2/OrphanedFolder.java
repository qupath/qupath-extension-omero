package qupath.ext.omero.core.entities.repositoryentities2;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.ApisHandler;

import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class OrphanedFolder implements RepositoryEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private final ApisHandler apisHandler;

    public OrphanedFolder(ApisHandler apisHandler) {
        this.apisHandler = apisHandler;
    }

    @Override
    public boolean hasChildren() {
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
        return String.format("Orphaned folder of %s", apisHandler.getWebServerURI());
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
