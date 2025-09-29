package qupath.ext.omero.core.entities.repositoryentities2.serverentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities2.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroPlate;

import java.net.URI;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class Plate extends ServerEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private final List<Attribute> attributes;

    public Plate(OmeroPlate omeroPlate, URI webServerUri) {
        super(omeroPlate.id(), omeroPlate.name(), omeroPlate.owner(), omeroPlate.group(), webServerUri);

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
        //TODO: get plate acquisitions of this plate
        //TODO: get wells of this plate

        return null;
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
