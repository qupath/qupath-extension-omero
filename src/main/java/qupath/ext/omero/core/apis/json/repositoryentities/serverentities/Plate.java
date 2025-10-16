package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroPlate;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Represents an OMERO plate.
 * <p>
 * A plate contains {@link PlateAcquisition plate acquisitions} and {@link Well wells}.
 */
public class Plate extends ServerEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private final int columns;
    private final int rows;
    private final List<Attribute> attributes;

    /**
     * Create a plate from an {@link OmeroPlate}.
     *
     * @param omeroPlate the OMERO plate to create the plate from
     * @param webServerUri the URI of the web server owning this entity
     * @throws NullPointerException if one of the provided parameters is null
     */
    public Plate(OmeroPlate omeroPlate, URI webServerUri) {
        super(
                omeroPlate.id(),
                omeroPlate.name(),
                omeroPlate.owner().orElse(null),
                omeroPlate.group().orElse(null),
                webServerUri
        );

        this.columns = omeroPlate.columns();
        this.rows = omeroPlate.rows();

        this.attributes = List.of(
                new Attribute(resources.getString("Entities.Plate.name"), name == null || name.isEmpty() ? getLabel() : name),
                new Attribute(resources.getString("Entities.Plate.id"), String.valueOf(id)),
                new Attribute(
                        resources.getString("Entities.Plate.owner"),
                        owner == null || owner.name() == null || owner.name().isEmpty() ? "-" : owner.name()
                ),
                new Attribute(
                        resources.getString("Entities.Plate.group"),
                        group == null || group.name() == null || group.name().isEmpty() ? "-" : group.name()
                ),
                new Attribute(resources.getString("Entities.Plate.columns"), String.valueOf(omeroPlate.columns())),
                new Attribute(resources.getString("Entities.Plate.rows"), String.valueOf(omeroPlate.rows()))
        );
    }

    @Override
    public List<Attribute> getAttributes() {
        return attributes;
    }

    /**
     * @return true since there is no way to know that this plate has children before its children are populated
     */
    @Override
    public boolean hasChildren() {
        return true;
    }

    /**
     * Returns the list of children of this element belonging to the provided experimenter and group.
     * <p>
     * Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally if the request failed for example).
     * <p>
     * Children that don't have any children are not returned by this function.
     *
     * @param ownerId the ID of the experimenter that should own the entities to retrieve
     * @param groupId the ID of the group that should own the entities to retrieve
     * @return a CompletableFuture (that may complete exceptionally) with the list of children of this entity
     */
    @Override
    public CompletableFuture<? extends List<? extends RepositoryEntity>> getChildren(long ownerId, long groupId) {
        Optional<Client> client = Client.getClientFromURI(webServerUri);

        if (client.isPresent()) {
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
