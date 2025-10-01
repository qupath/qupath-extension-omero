package qupath.ext.omero.core.entities.repositoryentities2.serverentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.entities.repositoryentities2.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroScreen;

import java.net.URI;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class Screen extends ServerEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private final int childCount;
    private final List<Attribute> attributes;

    public Screen(OmeroScreen omeroScreen, URI webServerUri) {
        super(omeroScreen.id(), omeroScreen.name(), omeroScreen.owner(), omeroScreen.group(), webServerUri);

        this.childCount = omeroScreen.childCount();

        String description = omeroScreen.description();
        this.attributes = List.of(
                new Attribute(resources.getString("Entities.Screen.name"), name == null || name.isEmpty() ? "-" : name),
                new Attribute(resources.getString("Entities.Screen.id"), String.valueOf(id)),
                new Attribute(
                        resources.getString("Entities.Screen.description"),
                        description == null || description.isEmpty() ? "-" : description
                ),
                new Attribute(
                        resources.getString("Entities.Screen.owner"),
                        owner == null || owner.getFullName().isEmpty() ? "-" : owner.getFullName()
                ),
                new Attribute(
                        resources.getString("Entities.Screen.group"),
                        group == null || group.getName().isEmpty() ? "-" : group.getName()
                ),
                new Attribute(resources.getString("Entities.Screen.nbPlates"), String.valueOf(childCount))
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
}
