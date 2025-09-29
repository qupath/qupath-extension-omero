package qupath.ext.omero.core.entities.repositoryentities2.serverentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.entities.repositoryentities2.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroProject;

import java.net.URI;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class Project extends ServerEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private final int childCount;
    private final List<Attribute> attributes;

    public Project(OmeroProject omeroProject, URI webServerUri) {
        super(omeroProject.id(), omeroProject.name(), omeroProject.owner(), omeroProject.group(), webServerUri);

        this.childCount = omeroProject.childCount();

        String description = omeroProject.description();
        this.attributes = List.of(
                new Attribute(resources.getString("Entities.Project.name"), name == null || name.isEmpty() ? "-" : name),
                new Attribute(resources.getString("Entities.Project.id"), String.valueOf(id)),
                new Attribute(
                        resources.getString("Entities.Project.description"),
                        description == null || description.isEmpty() ? "-" : description
                ),
                new Attribute(
                        resources.getString("Entities.Project.owner"),
                        owner == null || owner.getFullName().isEmpty() ? "-" : owner.getFullName()
                ),
                new Attribute(
                        resources.getString("Entities.Project.group"),
                        group == null || group.getName().isEmpty() ? "-" : group.getName()
                ),
                new Attribute(resources.getString("Entities.Project.nbImages"), String.valueOf(childCount))
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
        var client = Client.getClientFromURI(webServerUri);

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
        //TODO: localize
        return String.format("%s (%d)", name == null ? String.format("Project %d", id) : name, childCount);
    }

    @Override
    public String toString() {
        return String.format("Project %s of ID %d", name, id);
    }
}
