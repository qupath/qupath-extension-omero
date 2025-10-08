package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroDataset;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an OMERO dataset.
 * <p>
 * A dataset contains {@link Image images}.
 */
public class Dataset extends ServerEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private final int childCount;
    private final List<Attribute> attributes;

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
                omeroDataset.owner().orElse(null),
                omeroDataset.group().orElse(null),
                webServerUri
        );

        this.childCount = omeroDataset.childCount();

        String description = omeroDataset.description();
        this.attributes = List.of(
                new Attribute(resources.getString("Entities.Dataset.name"), name == null || name.isEmpty() ? "-" : name),
                new Attribute(resources.getString("Entities.Dataset.id"), String.valueOf(id)),
                new Attribute(
                        resources.getString("Entities.Dataset.description"),
                        description == null || description.isEmpty() ? "-" : description
                ),
                new Attribute(
                        resources.getString("Entities.Dataset.owner"),
                        owner == null || owner.name() == null || owner.name().isEmpty() ? "-" : owner.name()
                ),
                new Attribute(
                        resources.getString("Entities.Dataset.group"),
                        group == null || group.name() == null || group.name().isEmpty() ? "-" : group.name()
                ),
                new Attribute(resources.getString("Entities.Dataset.nbImages"), String.valueOf(childCount))
        );
    }

    @Override
    public List<Attribute> getAttributes() {
        return attributes;
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
}
