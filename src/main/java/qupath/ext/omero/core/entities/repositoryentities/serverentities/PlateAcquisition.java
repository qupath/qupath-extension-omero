package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.omeroentities.server.OmeroPlateAcquisition;

import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

public class PlateAcquisition extends ServerEntity {

    private static final DateFormat ACQUISITION_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final ResourceBundle resources = Utils.getResources();
    private final List<Integer> wellSampleIndices;
    private final boolean hasChildren;
    private final List<Attribute> attributes;

    public PlateAcquisition(OmeroPlateAcquisition omeroPlateAcquisition, int numberOfWells, URI webServerURI) {
        super(
                omeroPlateAcquisition.id(),
                omeroPlateAcquisition.name(),
                omeroPlateAcquisition.owner(),
                omeroPlateAcquisition.group(),
                webServerURI
        );

        this.wellSampleIndices = omeroPlateAcquisition.wellSampleIndices();
        this.hasChildren = numberOfWells > 0;

        this.attributes = List.of(
                new Attribute(resources.getString("Entities.PlateAcquisition.name"), name == null || name.isEmpty() ? getLabel() : name),
                new Attribute(resources.getString("Entities.PlateAcquisition.id"), String.valueOf(id)),
                new Attribute(
                        resources.getString("Entities.PlateAcquisition.owner"),
                        owner == null || owner.getFullName().isEmpty() ? "-" : owner.getFullName()
                ),
                new Attribute(
                        resources.getString("Entities.PlateAcquisition.group"),
                        group == null || group.getName().isEmpty() ? "-" : group.getName()
                ),
                new Attribute(
                        resources.getString("Entities.PlateAcquisition.acquisitionTime"),
                        omeroPlateAcquisition.startTime() == 0 ? "-" : ACQUISITION_DATE_FORMAT.format(new Date(omeroPlateAcquisition.startTime()))
                )
        );
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
        var client = Client.getClientFromURI(webServerUri);

        if (client.isPresent()) {
            //TODO: initial implementation was more complex
            return CompletableFuture.supplyAsync(() -> IntStream.range(wellSampleIndices.getFirst(), wellSampleIndices.get(1)+1)
                    .mapToObj(i -> client.get().getApisHandler().getWellsFromPlateAcquisition(id, ownerId, groupId, i))
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .filter(Well::hasChildren)
                    .toList()
            );
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException(String.format(
                    "Could not find the web client corresponding to %s. Impossible to get the children of this plate acquisition (%s).",
                    webServerUri,
                    this
            )));
        }
    }

    @Override
    public String getLabel() {
        return this.name == null ? String.format("Run %d", id) : this.name;
    }

    @Override
    public String toString() {
        return String.format("Plate acquisition %s of ID %d", name, id);
    }

    public Optional<Integer> getMinWellSampleIndex() {
        return Optional.ofNullable(wellSampleIndices)
                .map(wellSampleIndices -> wellSampleIndices.isEmpty() ? null : wellSampleIndices.getFirst());
    }

    public Optional<Integer> getMaxWellSampleIndex() {
        return Optional.ofNullable(wellSampleIndices)
                .map(wellSampleIndices -> wellSampleIndices.size() > 1 ? wellSampleIndices.get(1) : null);
    }
}
