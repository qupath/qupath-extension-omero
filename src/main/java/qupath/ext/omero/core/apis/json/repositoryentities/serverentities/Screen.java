package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroScreen;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an OMERO screen.
 * <p>
 * A dataset contains {@link Plate plates}.
 */
public class Screen extends ServerEntity {

    private final int childCount;
    private final String description;

    /**
     * Create a screen from an {@link OmeroScreen}.
     *
     * @param omeroScreen the OMERO screen to create the screen from
     * @param webServerUri the URI of the web server owning this entity
     * @throws NullPointerException if one of the provided parameters is null
     */
    public Screen(OmeroScreen omeroScreen, URI webServerUri) {
        super(
                omeroScreen.id(),
                omeroScreen.name(),
                omeroScreen.omeroDetails().experimenter().id(),
                omeroScreen.omeroDetails().group().id(),
                webServerUri
        );

        this.childCount = omeroScreen.childCount();
        this.description = omeroScreen.description();
    }

    @Override
    public boolean hasChildren() {
        return childCount > 0;
    }

    @Override
    public CompletableFuture<? extends List<? extends RepositoryEntity>> getChildren(long ownerId, long groupId) {
        Optional<Client> client = Client.getClientFromURI(webServerUri);

        if (client.isPresent()) {
            return client.get().getApisHandler().getPlates(id, ownerId, groupId);
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException(String.format(
                    "Could not find the web client corresponding to %s. Impossible to get the children of this screen (%s).",
                    webServerUri,
                    this
            )));
        }
    }

    @Override
    public String getLabel() {
        return String.format("%s (%d)", name == null ? String.format("Screen %d", id) : name, childCount);
    }

    @Override
    public String toString() {
        return String.format("Screen %s of ID %d", name, id);
    }

    /**
     * @return the number of plates this screen contains
     */
    public int getChildCount() {
        return childCount;
    }

    /**
     * @return a description of this screen, or an empty Optional if not provided
     */
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }
}
