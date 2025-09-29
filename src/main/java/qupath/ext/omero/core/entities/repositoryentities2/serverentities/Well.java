package qupath.ext.omero.core.entities.repositoryentities2.serverentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities2.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroWell;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroWellSample;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class Well extends ServerEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private final List<OmeroWellSample> wellSamples;
    private final int column;
    private final int row;
    private final long plateAcquisitionOwnerId;
    private final List<Attribute> attributes;

    public Well(OmeroWell omeroWell, long plateAcquisitionOwnerId, URI webServerUri) {
        super(omeroWell.id(), omeroWell.name(), omeroWell.owner(), omeroWell.group(), webServerUri);

        this.wellSamples = omeroWell.wellSamples();
        this.column = omeroWell.column();
        this.row = omeroWell.row();
        this.plateAcquisitionOwnerId = plateAcquisitionOwnerId;

        this.attributes = List.of(
                new Attribute(resources.getString("Entities.Well.name"), name == null || name.isEmpty() ? "-" : name),
                new Attribute(resources.getString("Entities.Well.id"), String.valueOf(id)),
                new Attribute(
                        resources.getString("Entities.Well.owner"),
                        owner == null || owner.getFullName().isEmpty() ? "-" : owner.getFullName()
                ),
                new Attribute(
                        resources.getString("Entities.Well.group"),
                        group == null || group.getName().isEmpty() ? "-" : group.getName()
                ),
                new Attribute(resources.getString("Entities.Well.column"), String.valueOf(column)),
                new Attribute(resources.getString("Entities.Well.row"), String.valueOf(row))
        );
    }

    @Override
    public List<Attribute> getAttributes() {
        return attributes;
    }

    @Override
    public boolean hasChildren() {
        return wellSamples != null && wellSamples.stream()
                .anyMatch(wellSample -> {
                    if (plateAcquisitionOwnerId > -1) {
                        return wellSample.plateAcquisition() != null && wellSample.plateAcquisition().id() == plateAcquisitionOwnerId;
                    } else {
                        return wellSample.plateAcquisition() == null;
                    }
                });
    }

    @Override
    public CompletableFuture<List<? extends RepositoryEntity>> getChildren(Owner owner, Group group) {
        //TODO: get children

        return null;
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
                .map(ServerEntity::getId)
                .toList();
    }
}
