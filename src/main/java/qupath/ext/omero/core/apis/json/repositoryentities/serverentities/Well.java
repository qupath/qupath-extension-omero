package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroWell;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroWellSample;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroImage;

import java.net.URI;
import java.util.List;
import java.util.Objects;
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
        this.column = omeroWell.column();
        this.row = omeroWell.row();
        this.plateAcquisitionOwnerId = plateAcquisitionOwnerId;

        this.attributes = List.of(
                new Attribute(resources.getString("Entities.Well.name"), name == null || name.isEmpty() ? "-" : name),
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
        //TODO: initial implementation was more complex
        return CompletableFuture.completedFuture(wellSamples.stream()
                .filter(wellSample -> {
                    if (plateAcquisitionOwnerId > -1) {
                        return wellSample.plateAcquisition() != null && wellSample.plateAcquisition().id() == plateAcquisitionOwnerId;
                    } else {
                        return wellSample.plateAcquisition() == null;
                    }
                })
                .map(OmeroWellSample::image)
                .filter(Objects::nonNull)
                .map(omeroImage -> new Image(omeroImage, webServerUri))
                .filter(image -> image.getOwner().isPresent() && image.getOwner().get().id() == ownerId)
                .filter(image -> image.getGroup().isPresent() && image.getGroup().get().id() == groupId)
                .toList()
        );
    }

    @Override
    public String getLabel() {
        return String.format("%s%d", (char) ('A' + row), column+1);
    }

    @Override
    public String toString() {
        return String.format("Well of ID %d", id);
    }

    public List<Long> getImageIds(long plateAcquisitionOwnerId) {
        return wellSamples == null ? List.of() : wellSamples.stream()
                .filter(wellSample ->
                        plateAcquisitionOwnerId < 0 || (wellSample.plateAcquisition() != null && wellSample.plateAcquisition().id() == plateAcquisitionOwnerId)
                )
                .map(OmeroWellSample::image)
                .filter(Objects::nonNull)
                .map(OmeroImage::id)
                .toList();
    }
}
