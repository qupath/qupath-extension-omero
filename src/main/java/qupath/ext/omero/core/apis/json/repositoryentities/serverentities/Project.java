package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroProject;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an OMERO project.
 * <p>
 * A project contains {@link Dataset datasets}.
 */
public class Project extends ServerEntity {

    private final int childCount;
    private final String description;

    /**
     * Create a project from an {@link OmeroProject}.
     *
     * @param omeroProject the OMERO project to create the project from
     * @param webServerUri the URI of the web server owning this entity
     * @throws NullPointerException if one of the provided parameters is null
     */
    public Project(OmeroProject omeroProject, URI webServerUri) {
        super(
                omeroProject.id(),
                omeroProject.name(),
                omeroProject.omeroDetails().experimenter().id(),
                omeroProject.omeroDetails().group().id(),
                webServerUri
        );

        this.childCount = omeroProject.childCount();
        this.description = omeroProject.description();
    }

    @Override
    public boolean hasChildren() {
        return childCount > 0;
    }

    @Override
    public CompletableFuture<? extends List<? extends RepositoryEntity>> getChildren(long ownerId, long groupId) {
        Optional<Client> client = Client.getClientFromURI(webServerUri);

        if (client.isPresent()) {
            return client.get().getApisHandler().getDatasets(id, ownerId, groupId);
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException(String.format(
                    "Could not find the web client corresponding to %s. Impossible to get the children of this project (%s).",
                    webServerUri,
                    this
            )));
        }
    }

    @Override
    public String getLabel() {
        return String.format("%s (%d)", name == null ? String.format("Project %d", id) : name, childCount);
    }

    @Override
    public String toString() {
        return String.format("Project %s of ID %d", name, id);
    }

    /**
     * @return the number of datasets this project contains
     */
    public int getChildCount() {
        return childCount;
    }

    /**
     * @return a description of this project, or an empty Optional if not provided
     */
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }
}
