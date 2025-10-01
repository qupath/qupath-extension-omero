package qupath.ext.omero.core.entities.repositoryentities2.serverentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.entities.repositoryentities2.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroPlate;

import java.net.URI;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class Plate extends ServerEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private final int columns;
    private final int rows;
    private final List<Attribute> attributes;

    public Plate(OmeroPlate omeroPlate, URI webServerUri) {
        super(omeroPlate.id(), omeroPlate.name(), omeroPlate.owner(), omeroPlate.group(), webServerUri);

        this.columns = omeroPlate.columns();
        this.rows = omeroPlate.rows();

        this.attributes = List.of(
                new Attribute(resources.getString("Entities.Plate.name"), name == null || name.isEmpty() ? "-" : name),
                new Attribute(resources.getString("Entities.Plate.id"), String.valueOf(id)),
                new Attribute(
                        resources.getString("Entities.Plate.owner"),
                        owner == null || owner.getFullName().isEmpty() ? "-" : owner.getFullName()
                ),
                new Attribute(
                        resources.getString("Entities.Plate.group"),
                        group == null || group.getName().isEmpty() ? "-" : group.getName()
                ),
                new Attribute(resources.getString("Entities.Plate.columns"), String.valueOf(omeroPlate.columns())),
                new Attribute(resources.getString("Entities.Plate.rows"), String.valueOf(omeroPlate.rows()))
        );
    }

    @Override
    public List<Attribute> getAttributes() {
        return attributes;
    }

    @Override
    public boolean hasChildren() {
        // There is no way to know that this plate has children before its children are populated
        return true;
    }

    @Override
    public CompletableFuture<? extends List<? extends RepositoryEntity>> getChildren(long ownerId, long groupId) {
        var client = Client.getClientFromURI(webServerUri);

        if (client.isPresent()) {
            //TODO: get well was more complex in original code
            return CompletableFuture.supplyAsync(() ->
                    Stream.of(
                            client.get().getApisHandler().getPlateAcquisitions(id, ownerId, groupId, columns * rows),
                            client.get().getApisHandler().getWellsFromPlate(id, ownerId, groupId)
                    )
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .filter(RepositoryEntity::hasChildren)
                    .toList()
            );
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException(String.format(
                    "Could not find the web client corresponding to %s. Impossible to get the children of this plate (%s).",
                    webServerUri,
                    this
            )));
        }
    }

    @Override
    public String getLabel() {
        return name == null ? String.format("Plate %d", id) : name;
    }

    @Override
    public String toString() {
        return String.format("Plate %s of ID %d", name, id);
    }
}
