package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroDataset;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an OMERO dataset.
 * <p>
 * A dataset contains {@link Image images}.
 */
public class Dataset extends ServerEntity {

    private final int childCount;
    private final String description;

    /**
     * Create a dataset from an {@link OmeroDataset}.
     *
     * @param omeroDataset the OMERO dataset to create the dataset from
     * @param webServerUri the URI of the web server owning this entity
     * @throws NullPointerException if one of the provided parameters is null
     */
    public Dataset(OmeroDataset omeroDataset, URI webServerUri) {
        super(
                omeroDataset.id(),
                omeroDataset.name(),
                omeroDataset.omeroDetails().experimenter().id(),
                omeroDataset.omeroDetails().group().id(),
                webServerUri
        );

        this.childCount = omeroDataset.childCount();
        this.description = omeroDataset.description();
    }

    @Override
    public boolean hasChildren() {
        return childCount > 0;
    }

    @Override
    public CompletableFuture<? extends List<? extends RepositoryEntity>> getChildren(long ownerId, long groupId) {
        Optional<Client> client = Client.getClientFromURI(webServerUri);

        if (client.isPresent()) {
            return client.get().getApisHandler().getImages(id, ownerId, groupId);
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException(String.format(
                    "Could not find the web client corresponding to %s. Impossible to get the children of this dataset (%s).",
                    webServerUri,
                    this
            )));
        }
    }

    @Override
    public String getLabel() {
        return String.format("%s (%d)", name == null ? String.format("Dataset %d", id) : name, childCount);
    }

    @Override
    public String toString() {
        return String.format("Dataset %s of ID %d", name, id);
    }

    /**
     * @return the number of images this dataset contains
     */
    public int getChildCount() {
        return childCount;
    }

    /**
     * @return a description of this dataset, or an empty Optional if not provided
     */
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }
}
