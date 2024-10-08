package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import com.google.gson.annotations.SerializedName;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.gui.UiUtilities;

import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an OMERO plate acquisition.
 * A plate acquisition contains {@link Image images}.
 */
public class PlateAcquisition extends ServerEntity {

    private static final Logger logger = LoggerFactory.getLogger(PlateAcquisition.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Web.Entities.PlateAcquisition.name"),
            resources.getString("Web.Entities.PlateAcquisition.id"),
            resources.getString("Web.Entities.PlateAcquisition.owner"),
            resources.getString("Web.Entities.PlateAcquisition.group"),
            resources.getString("Web.Entities.PlateAcquisition.acquisitionTime")
    };
    private final transient ObservableList<Image> children = FXCollections.observableArrayList();
    private final transient ObservableList<Image> childrenImmutable = FXCollections.unmodifiableObservableList(children);
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
        return this.name == null ? "-" : this.name;
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
            case 3 -> getGroup().getName();
            case 4 -> startTime == 0 ? "-" : new Date(startTime).toString();
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
     * @param type  the OMERO entity type
     * @return whether this type refers to a plate acquisition
     */
    public static boolean isPlateAcquisition(String type) {
        return "http://www.openmicroscopy.org/Schemas/OME/2016-06#PlateAcquisition".equalsIgnoreCase(type) || "PlateAcquisition".equalsIgnoreCase(type);
    }

    /**
     * Set the number of wells of this plate acquisition.
     *
     * @param numberOfWells  the number of wells of this plate acquisition
     */
    public synchronized void setNumberOfWells(int numberOfWells) {
        this.numberOfWells = numberOfWells;
    }

    private void populateChildren() {
        if (webServerURI == null) {
            throw new IllegalStateException(
                    "The web server URI has not been set on this plate acquisition. See the setWebServerURI(URI) function."
            );
        } else {
            WebClients.getClientFromURI(webServerURI).ifPresentOrElse(client -> {
                isPopulating = true;

                int wellSampleIndex = 0;
                if (wellSampleIndices != null && wellSampleIndices.size() > 1) {
                    wellSampleIndex = wellSampleIndices.get(0);
                }

                client.getApisHandler().getWellsFromPlateAcquisition(getId(), wellSampleIndex)
                        .thenApplyAsync(wells -> wells.stream()
                                .map(well -> well.getImagesIds(true))
                                .flatMap(List::stream)
                                .map(id -> client.getApisHandler().getImage(id))
                                .map(CompletableFuture::join)
                                .toList()
                        )
                        .exceptionally(error -> {
                            logger.error("Error while retrieving wells", error);
                            return List.of();
                        })
                        .thenAccept(wells -> {
                            children.addAll(wells);
                            isPopulating = false;
                        });
            }, () -> logger.warn(String.format(
                    "Could not find the web client corresponding to %s. Impossible to get the children of this plate acquisition (%s).",
                    webServerURI,
                    this
            )));
        }
    }
}
