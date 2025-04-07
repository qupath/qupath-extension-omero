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

import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an OMERO well.
 * A well acquisition contains {@link Image} ids.
 */
public class Well extends ServerEntity {

    private static final Logger logger = LoggerFactory.getLogger(Well.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Entities.Well.name"),
            resources.getString("Entities.Well.id"),
            resources.getString("Entities.Well.owner"),
            resources.getString("Entities.Well.group"),
            resources.getString("Entities.Well.column"),
            resources.getString("Entities.Well.row")
    };
    private final transient ObservableList<Image> children = FXCollections.observableArrayList();
    private final transient ObservableList<Image> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final transient AtomicBoolean childrenPopulated = new AtomicBoolean(false);
    private transient volatile boolean isPopulating = false;
    private transient volatile long plateAcquisitionOwnerId = -1;
    @SerializedName(value = "WellSamples") private List<WellSample> wellSamples;
    @SerializedName(value = "Column") private int column;
    @SerializedName(value = "Row") private int row;
    private record WellSample(
            @SerializedName(value = "Image") Image image,
            @SerializedName(value = "PlateAcquisition") PlateAcquisition plateAcquisition
    ) {}

    /**
     * Creates an empty plate.
     */
    public Well() {
        // This constructor is declared because otherwise transient fields
        // of this class are not declared when it is created through JSON
    }

    /**
     * Creates an empty well only defined by its ID.
     */
    public Well(long id) {
        this.id = id;
    }

    @Override
    public boolean hasChildren() {
        return wellSamples != null && wellSamples.stream()
                .anyMatch(wellSample -> {
                    if (plateAcquisitionOwnerId > -1) {
                        return wellSample.plateAcquisition() != null && wellSample.plateAcquisition().getId() == plateAcquisitionOwnerId;
                    } else {
                        return wellSample.plateAcquisition == null;
                    }
                });
    }

    @Override
    public ObservableList<? extends RepositoryEntity> getChildren() {
        if (childrenPopulated.compareAndSet(false, true)) {
            populateChildren();
        }

        return childrenImmutable;
    }

    @Override
    public String getLabel() {
        return String.format("%s%d", (char) ('A' + row), column+1);
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
            case 4 -> String.valueOf(column);
            case 5 -> String.valueOf(row);
            default -> "";
        };
    }

    @Override
    public int getNumberOfAttributes() {
        return ATTRIBUTES.length;
    }

    @Override
    public String toString() {
        return String.format("Well of ID %d", id);
    }

    /**
     * Indicates if an OMERO entity type refers to a well.
     *
     * @param type the OMERO entity type
     * @return whether this type refers to a well
     */
    public static boolean isWell(String type) {
        return "http://www.openmicroscopy.org/Schemas/OME/2016-06#Well".equalsIgnoreCase(type) || "Well".equalsIgnoreCase(type);
    }

    /**
     * Set the ID of the plate acquisition owning this well. If this function is called before the first call to
     * {@link #getChildren()}, then the children of this well will all belong to the provided plate acquisition.
     * If this function is not called (or called with a negative ID), then the children of this well will not belong
     * to any plate acquisition.
     *
     * @param id the ID of the plate acquisition that should own all children of this well, or a negative number if the
     *           children of this well should not belong to any plate acquisition
     */
    public synchronized void setPlateAcquisitionOwnerId(long id) {
        plateAcquisitionOwnerId = id;
    }

    /**
     * Get the IDs of the images present in this well, optionally belonging to a plate acquisition.
     *
     * @param plateAcquisitionOwnerId the ID of the plate acquisition that should contain the images to retrieve,
     *                                or a negative to retrieve all images
     * @return all image IDs contained in this well belonging to the provided plate acquisition
     */
    public List<Long> getImageIds(long plateAcquisitionOwnerId) {
        return wellSamples == null ? List.of() : wellSamples.stream()
                .filter(wellSample ->
                        plateAcquisitionOwnerId < 0 || (wellSample.plateAcquisition() != null && wellSample.plateAcquisition().getId() == plateAcquisitionOwnerId)
                )
                .map(WellSample::image)
                .filter(Objects::nonNull)
                .map(ServerEntity::getId)
                .toList();
    }

    private void populateChildren() {
        if (webServerURI == null) {
            throw new IllegalStateException("The web server URI has not been set on this well. Cannot populate children");
        }

        Client.getClientFromURI(webServerURI).ifPresentOrElse(client -> {
            if (wellSamples == null) {
                logger.debug("Well sample list not found. Cannot populate children images of {}", this);
                return;
            }

            isPopulating = true;
            CompletableFuture.supplyAsync(() -> wellSamples.stream()
                    .filter(wellSample -> {
                        if (plateAcquisitionOwnerId > -1) {
                            return wellSample.plateAcquisition() != null && wellSample.plateAcquisition().getId() == plateAcquisitionOwnerId;
                        } else {
                            return wellSample.plateAcquisition == null;
                        }
                    })
                    .map(WellSample::image)
                    .filter(Objects::nonNull)
                    .map(image -> client.getApisHandler().getImage(image.getId()))
                    .map(CompletableFuture::join)
                    .toList()
            ).whenComplete((images, error) -> {
                isPopulating = false;

                if (images == null) {
                    logger.error("Error while retrieving children images of {}", this, error);
                    return;
                }

                logger.debug("Got images {} as children of {}", images, this);
                children.addAll(images);
            });
        }, () -> logger.warn(
                "Could not find the web client corresponding to {}. Impossible to get the children of this screen ({}).",
                webServerURI,
                this
        ));
    }
}
