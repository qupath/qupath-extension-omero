package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroSimpleImage;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroWell;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroWellSample;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an OMERO well.
 * <p>
 * A well contains {@link Image images}.
 */
public class Well extends ServerEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private final List<OmeroWellSample> wellSamples;
    private final int column;
    private final int row;
    private final long plateAcquisitionOwnerId;
    private final List<Attribute> attributes;
    private final boolean hasChildren;

    /**
     * Create a well from an {@link OmeroWell}.
     *
     * @param omeroWell the OMERO well to create the well from
     * @param plateAcquisitionOwnerId the ID of the plate acquisition owning this well. The children of this well will all belong
     *                                to the provided plate acquisition. Can be negative if the children of this well should not
     *                                belong to any plate acquisition
     * @param webServerUri the URI of the web server owning this entity
     * @throws NullPointerException if one of the provided parameters is null
     */
    public Well(OmeroWell omeroWell, long plateAcquisitionOwnerId, URI webServerUri) {
        super(
                omeroWell.id(),
                omeroWell.name(),
                omeroWell.owner().orElse(null),
                omeroWell.group().orElse(null),
                webServerUri
        );

        this.wellSamples = omeroWell.wellSamples();
        this.column = omeroWell.column() == null ? 0 : omeroWell.column();
        this.row = omeroWell.row() == null ? 0 : omeroWell.row();
        this.plateAcquisitionOwnerId = plateAcquisitionOwnerId;

        this.attributes = List.of(
                new Attribute(resources.getString("Entities.Well.name"), name == null || name.isEmpty() ? getLabel() : name),
                new Attribute(resources.getString("Entities.Well.id"), String.valueOf(id)),
                new Attribute(
                        resources.getString("Entities.Well.owner"),
                        owner == null || owner.name() == null || owner.name().isEmpty() ? "-" : owner.name()
                ),
                new Attribute(
                        resources.getString("Entities.Well.group"),
                        group == null || group.name() == null || group.name().isEmpty() ? "-" : group.name()
                ),
                new Attribute(resources.getString("Entities.Well.column"), String.valueOf(column)),
                new Attribute(resources.getString("Entities.Well.row"), String.valueOf(row))
        );

        this.hasChildren = wellSamples != null && wellSamples.stream()
                .anyMatch(wellSample -> {
                    if (plateAcquisitionOwnerId > -1) {
                        return wellSample.plateAcquisition() != null && wellSample.plateAcquisition().id() == plateAcquisitionOwnerId;
                    } else {
                        return wellSample.plateAcquisition() == null;
                    }
                });
    }

    @Override
    public List<Attribute> getAttributes() {
        return attributes;
    }

    @Override
    public boolean hasChildren() {
        return hasChildren;
    }

    @Override
    public CompletableFuture<? extends List<? extends RepositoryEntity>> getChildren(long ownerId, long groupId) {
        Optional<Client> client = Client.getClientFromURI(webServerUri);

        if (client.isPresent()) {
            return CompletableFuture.supplyAsync(() -> wellSamples.stream()
                     .filter(wellSample -> {
                         if (plateAcquisitionOwnerId > -1) {
                             return wellSample.plateAcquisition() != null && wellSample.plateAcquisition().id() == plateAcquisitionOwnerId;
                         } else {
                             return wellSample.plateAcquisition() == null;
                         }
                     })
                     .map(OmeroWellSample::image)
                     .filter(Objects::nonNull)
                     .map(omeroSimpleImage -> client.get().getApisHandler().getImage(omeroSimpleImage.id()))
                     .map(CompletableFuture::join)
                     .filter(image -> ownerId < 0 || image.getOwner().isPresent() && image.getOwner().get().id() == ownerId)
                     .filter(image -> groupId < 0 || image.getGroup().isPresent() && image.getGroup().get().id() == groupId)
                     .toList());
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
        return String.format("%s%d", (char) ('A' + row), column+1);
    }

    @Override
    public String toString() {
        return String.format("Well of ID %d", id);
    }

    /**
     * Get a list of image IDs contained in the well samples of this well and belonging to the provided plate acquisition.
     *
     * @param plateAcquisitionOwnerId the ID of a plate acquisition that should own the returned images. Can be negative to
     *                                retrieve all images
     * @return a list of image IDs contained in the well samples of this well and belonging to the provided plate acquisition
     */
    public List<Long> getImageIds(long plateAcquisitionOwnerId) {
        return wellSamples == null ? List.of() : wellSamples.stream()
                .filter(wellSample ->
                        plateAcquisitionOwnerId < 0 || (wellSample.plateAcquisition() != null && wellSample.plateAcquisition().id() == plateAcquisitionOwnerId)
                )
                .map(OmeroWellSample::image)
                .filter(Objects::nonNull)
                .map(OmeroSimpleImage::id)
                .toList();
    }
}
