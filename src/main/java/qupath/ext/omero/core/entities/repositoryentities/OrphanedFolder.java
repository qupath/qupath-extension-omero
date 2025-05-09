package qupath.ext.omero.core.entities.repositoryentities;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;

import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An orphaned folder is a container for orphaned images (which are described in
 * {@link qupath.ext.omero.core.entities.repositoryentities.serverentities server entities}).
 */
public class OrphanedFolder implements RepositoryEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private final ObservableList<Image> children = FXCollections.observableArrayList();
    private final ObservableList<Image> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final ApisHandler apisHandler;
    private final int numberOfImages;
    private final transient AtomicBoolean childrenPopulated = new AtomicBoolean(false);

    private OrphanedFolder(ApisHandler apisHandler, int numberOfImages) {
        this.apisHandler = apisHandler;
        this.numberOfImages = numberOfImages;
    }

    /**
     * <p>Creates a new orphaned folder and request its number of children.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param apisHandler the apis handler of the server
     * @return a CompletableFuture (that may complete exceptionally) with the new orphaned folder
     */
    public static CompletableFuture<OrphanedFolder> create(ApisHandler apisHandler) {
        return apisHandler.getOrphanedImagesIds().thenApply(ids -> new OrphanedFolder(apisHandler, ids.size()));
    }

    @Override
    public String toString() {
        return String.format("Orphaned folder containing %d children", numberOfImages);
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

    @Override
    public boolean hasChildren() {
        return numberOfImages > 0;
    }

    @Override
    public ObservableList<? extends RepositoryEntity> getChildren() {
        if (childrenPopulated.compareAndSet(false, true)) {
            apisHandler.populateOrphanedImagesIntoList(children);
        }

        return childrenImmutable;
    }

    @Override
    public String getLabel() {
        return resources.getString("Entities.RepositoryEntities.OrphanedFolder.orphanedImages");
    }

    @Override
    public boolean isPopulatingChildren() {
        return apisHandler.areOrphanedImagesLoading().get();
    }
}
