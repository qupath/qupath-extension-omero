package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import com.google.gson.annotations.SerializedName;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;

import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an OMERO plate. A plate contains {@link PlateAcquisition plate acquisitions}
 * and {@link Well wells}.
 */
public class Plate extends ServerEntity {

    private static final Logger logger = LoggerFactory.getLogger(Plate.class);
    private static final int NUMBER_OF_CHILDREN_TYPES = 2;
    private static final ResourceBundle resources = Utils.getResources();
    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Entities.Plate.name"),
            resources.getString("Entities.Plate.id"),
            resources.getString("Entities.Plate.owner"),
            resources.getString("Entities.Plate.group"),
            resources.getString("Entities.Plate.columns"),
            resources.getString("Entities.Plate.rows")
    };
    private final transient ObservableList<ServerEntity> children = FXCollections.observableList(new CopyOnWriteArrayList<>());
    private final transient ObservableList<ServerEntity> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final transient AtomicBoolean childrenPopulated = new AtomicBoolean(false);
    private int childrenTypesPopulated = 0;
    @SerializedName(value = "Columns") private int columns;
    @SerializedName(value = "Rows") private int rows;

    /**
     * Creates an empty plate.
     */
    public Plate() {
        // This constructor is declared because otherwise transient fields
        // of this class are not declared when it is created through JSON
    }

    /**
     * Creates an empty plate only defined by its ID.
     */
    public Plate(long id) {
        this.id = id;
    }

    @Override
    public boolean hasChildren() {
        // There is no way to know that this plate has children before its children are populated
        if (!childrenPopulated.get()) {
            return true;
        } else {
            return !children.isEmpty();
        }
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
        return name == null ? String.format("Plate %d", id) : name;
    }

    @Override
    public synchronized boolean isPopulatingChildren() {
        return childrenTypesPopulated != NUMBER_OF_CHILDREN_TYPES;
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
            case 4 -> String.valueOf(columns);
            case 5 -> String.valueOf(rows);
            default -> "";
        };
    }

    @Override
    public int getNumberOfAttributes() {
        return ATTRIBUTES.length;
    }

    @Override
    public String toString() {
        return String.format("Plate %s of ID %d", name, id);
    }

    /**
     * Indicates if an OMERO entity type refers to a plate.
     *
     * @param type the OMERO entity type
     * @return whether this type refers to a plate
     */
    public static boolean isPlate(String type) {
        return "http://www.openmicroscopy.org/Schemas/OME/2016-06#Plate".equalsIgnoreCase(type) || "Plate".equalsIgnoreCase(type);
    }

    private void populateChildren() {
        if (webServerURI == null) {
            throw new IllegalStateException("The web server URI has not been set on this plate. Cannot populate children");
        }

        Client.getClientFromURI(webServerURI).ifPresentOrElse(client -> {
            client.getApisHandler().getPlateAcquisitions(id).whenComplete(((plateAcquisitions, error) -> {
                synchronized (this) {
                    childrenTypesPopulated++;
                }

                if (plateAcquisitions == null) {
                    logger.error("Error while retrieving children plate acquisitions of {}", this, error);
                    return;
                }

                logger.debug("Got plate acquisitions {} as children of {}", plateAcquisitions, this);
                for (PlateAcquisition plateAcquisition: plateAcquisitions) {
                    plateAcquisition.setNumberOfWells(columns * rows);
                }
                children.addAll(plateAcquisitions);
            }));

            client.getApisHandler().getWellsFromPlate(id).thenApply(wells ->
                    wells.stream().map(ServerEntity::getId).distinct().toList()
            ).thenApplyAsync(wellIds -> {
                logger.debug("Got well IDs {} for {}. Fetching corresponding wells", wellIds, this);

                return wellIds.stream()
                        .map(id -> client.getApisHandler().getWell(id))
                        .map(CompletableFuture::join)
                        .toList();
            }).whenComplete((wells, error) -> {
                synchronized (this) {
                    childrenTypesPopulated++;
                }

                if (wells == null) {
                    logger.error("Error while retrieving children wells of {}", this, error);
                    return;
                }

                List<Well> filteredWells = wells.stream()
                        .filter(Well::hasChildren)
                        .toList();

                logger.debug("Got wells {} filtered to {} by removing empty ones as children of {}", wells, filteredWells, this);
                children.addAll(filteredWells);
            });
        }, () -> logger.warn(
                "Could not find the web client corresponding to {}. Impossible to get the children of this plate ({}).",
                webServerURI,
                this
        ));
    }
}
