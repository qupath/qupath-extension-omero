package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import com.google.gson.annotations.SerializedName;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * Represents an OMERO plate acquisition.
 * A plate acquisition contains {@link Image images}.
 */
public class PlateAcquisition extends ServerEntity {

    private static final Logger logger = LoggerFactory.getLogger(PlateAcquisition.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Entities.PlateAcquisition.name"),
            resources.getString("Entities.PlateAcquisition.id"),
            resources.getString("Entities.PlateAcquisition.owner"),
            resources.getString("Entities.PlateAcquisition.group"),
            resources.getString("Entities.PlateAcquisition.acquisitionTime")
    };
    private static final DateFormat ACQUISITION_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final transient ObservableList<Well> children = FXCollections.observableArrayList();
    private final transient ObservableList<Well> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final transient AtomicBoolean childrenPopulated = new AtomicBoolean(false);
    private transient volatile boolean isPopulating = false;
    private transient int numberOfWells = 0;
    @SerializedName(value = "omero:wellsampleIndex") private List<Integer> wellSampleIndices;
    @SerializedName(value = "StartTime") private long startTime;

    /**
     * Creates an empty plate acquisition.
     */
    public PlateAcquisition() {
        // This constructor is declared because otherwise transient fields
        // of this class are not declared when it is created through JSON
    }

    /**
     * Creates an empty plate acquisition only defined by its ID.
     */
    public PlateAcquisition(long id) {
        this.id = id;
    }

    @Override
    public synchronized boolean hasChildren() {
        return numberOfWells > 0;
    }

    /**
     * @throws IllegalStateException when the web server URI has not been set
     */
    @Override
    public ObservableList<? extends RepositoryEntity> getChildren() {
        if (childrenPopulated.compareAndSet(false, true)) {
            populateChildren();
        }

        return childrenImmutable;
    }

    @Override
    public String getLabel() {
        return this.name == null ? String.format("Run %d", id) : this.name;
    }

    @Override
    public boolean isPopulatingChildren() {
        return isPopulating;
    }

    @Override
    public String getAttributeName(int informationIndex) {
        if (informationIndex < ATTRIBUTES.length) {
            return ATTRIBUTES[informationIndex];
        } else {
            return "";
        }
    }

    @Override
    public String getAttributeValue(int informationIndex) {
        return switch (informationIndex) {
            case 0 -> name == null || name.isEmpty() ? "-" : name;
            case 1 -> String.valueOf(getId());
            case 2 -> getOwner().getFullName();
            case 3 -> getGroupName();
            case 4 -> startTime == 0 ? "-" : ACQUISITION_DATE_FORMAT.format(new Date(startTime));
            default -> "";
        };
    }

    @Override
    public int getNumberOfAttributes() {
        return ATTRIBUTES.length;
    }

    @Override
    public String toString() {
        return String.format("Plate acquisition %s of ID %d", name, id);
    }

    /**
     * Indicates if an OMERO entity type refers to a plate acquisition.
     *
     * @param type the OMERO entity type
     * @return whether this type refers to a plate acquisition
     */
    public static boolean isPlateAcquisition(String type) {
        return "http://www.openmicroscopy.org/Schemas/OME/2016-06#PlateAcquisition".equalsIgnoreCase(type) || "PlateAcquisition".equalsIgnoreCase(type);
    }

    /**
     * Set the number of wells of this plate acquisition.
     *
     * @param numberOfWells the number of wells of this plate acquisition
     */
    public synchronized void setNumberOfWells(int numberOfWells) {
        this.numberOfWells = numberOfWells;
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

    private void populateChildren() {
        if (webServerURI == null) {
            throw new IllegalStateException("The web server URI has not been set on this plate acquisition. Cannot populate children");
        }

        Client.getClientFromURI(webServerURI).ifPresentOrElse(client -> {
            isPopulating = true;

            if (wellSampleIndices == null || wellSampleIndices.size() < 2) {
                isPopulating = false;
                logger.debug("Well sample indice list not found or contains less than 2 elements. Cannot populate children wells of {}", this);
                return;
            }

            CompletableFuture.supplyAsync(() -> IntStream.range(wellSampleIndices.getFirst(), wellSampleIndices.get(1)+1)
                    .mapToObj(i -> client.getApisHandler().getWellsFromPlateAcquisition(id, i))
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .map(ServerEntity::getId)
                    .distinct()
                    .toList()
            ).thenApplyAsync(wellIds -> {
                logger.debug("Got well IDs {} for {}. Fetching corresponding wells", wellIds, this);

                return wellIds.stream()
                        .map(id -> client.getApisHandler().getWell(id))
                        .map(CompletableFuture::join)
                        .toList();
            }).whenComplete((wells, error) -> {
                isPopulating = false;

                if (wells == null) {
                    logger.error("Error while retrieving children wells of {}", this, error);
                    return;
                }

                for (Well well: wells) {
                    well.setPlateAcquisitionOwnerId(id);
                }

                logger.debug("Got wells {} as children of {}", wells, this);
                children.addAll(wells);
            });
        }, () -> logger.warn(
                "Could not find the web client corresponding to {}. Impossible to get the children of this plate acquisition ({}).",
                webServerURI,
                this
        ));
    }
}
