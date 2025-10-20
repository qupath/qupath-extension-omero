package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroPlateAcquisition;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * Represents an OMERO plate acquisition.
 * <p>
 * A plate acquisition contains {@link Well wells}.
 */
public class PlateAcquisition extends ServerEntity {

    private final List<Integer> wellSampleIndices;
    private final boolean hasChildren;
    private final Date startTime;

    /**
     * Create a plate acquisition from an {@link OmeroPlateAcquisition}.
     *
     * @param omeroPlateAcquisition the OMERO plate acquisition to create the plate acquisition from
     * @param numberOfWells the number of wells this plate acquisition should have
     * @param webServerUri the URI of the web server owning this entity
     * @throws NullPointerException if one of the provided parameters is null
     */
    public PlateAcquisition(OmeroPlateAcquisition omeroPlateAcquisition, int numberOfWells, URI webServerUri) {
        super(
                omeroPlateAcquisition.id(),
                omeroPlateAcquisition.name(),
                omeroPlateAcquisition.omeroDetails().experimenter().id(),
                omeroPlateAcquisition.omeroDetails().group().id(),
                webServerUri
        );

        this.wellSampleIndices = omeroPlateAcquisition.wellSampleIndices();
        this.hasChildren = numberOfWells > 0;
        this.startTime = omeroPlateAcquisition.startTime() == null ? null : new Date(omeroPlateAcquisition.startTime());
    }

    @Override
    public boolean hasChildren() {
        return hasChildren;
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

    /**
     * @return the minimal index of well sample this plate acquisition contains, or an empty Optional if not found
     */
    public Optional<Integer> getMinWellSampleIndex() {
        return Optional.ofNullable(wellSampleIndices)
                .map(wellSampleIndices -> wellSampleIndices.isEmpty() ? null : wellSampleIndices.getFirst());
    }

    /**
     * @return the maximal index of well sample this plate acquisition contains, or an empty Optional if not found
     */
    public Optional<Integer> getMaxWellSampleIndex() {
        return Optional.ofNullable(wellSampleIndices)
                .map(wellSampleIndices -> wellSampleIndices.size() > 1 ? wellSampleIndices.get(1) : null);
    }

    /**
     * @return the date corresponding to when the first image of this plate acquisition was collected, or an empty Optional if not found
     */
    public Optional<Date> getStartTime() {
        return Optional.ofNullable(startTime);
    }
}
