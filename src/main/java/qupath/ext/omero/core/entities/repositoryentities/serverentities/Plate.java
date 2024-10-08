package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.gui.UiUtilities;

import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an OMERO plate.
 * A plate contains {@link PlateAcquisition plate acquisitions}
 * and {@link Well wells}.
 */
public class Plate extends ServerEntity {

    private static final Logger logger = LoggerFactory.getLogger(Plate.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Web.Entities.Plate.name"),
            resources.getString("Web.Entities.Plate.id"),
            resources.getString("Web.Entities.Plate.owner"),
            resources.getString("Web.Entities.Plate.group"),
            resources.getString("Web.Entities.Plate.columns"),
            resources.getString("Web.Entities.Plate.rows")
    };
    private final transient ObservableList<ServerEntity> children = FXCollections.observableArrayList();
    private final transient ObservableList<ServerEntity> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final transient AtomicBoolean childrenPopulated = new AtomicBoolean(false);
    private transient volatile boolean isPopulating = false;
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
    public ReadOnlyStringProperty getLabel() {
        return new SimpleStringProperty(name == null ? "" : name);
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
     * @param type  the OMERO entity type
     * @return whether this type refers to a plate
     */
    public static boolean isPlate(String type) {
        return "http://www.openmicroscopy.org/Schemas/OME/2016-06#Plate".equalsIgnoreCase(type) || "Plate".equalsIgnoreCase(type);
    }

    private void populateChildren() {
        if (webServerURI == null) {
            throw new IllegalStateException(
                    "The web server URI has not been set on this plate. See the setWebServerURI(URI) function."
            );
        } else {
            WebClients.getClientFromURI(webServerURI).ifPresentOrElse(client -> {
                isPopulating = true;
                client.getApisHandler().getPlateAcquisitions(getId()).thenCompose(plateAcquisitions -> {
                    for (PlateAcquisition plateAcquisition: plateAcquisitions) {
                        plateAcquisition.setNumberOfWells(columns * rows);
                    }

                    children.addAll(plateAcquisitions);
                    return client.getApisHandler().getWellsFromPlate(getId());
                }).thenAcceptAsync(wells -> {
                    List<Long> ids = wells.stream()
                            .map(well -> well.getImagesIds(false))
                            .flatMap(List::stream)
                            .toList();
                    List<List<Long>> batches = Lists.partition(ids, 16);

                    for (List<Long> batch: batches) {
                        children.addAll(batch.stream()
                                .map(id -> client.getApisHandler().getImage(id))
                                .map(CompletableFuture::join)
                                .flatMap(Optional::stream)
                                .toList());
                    }

                    isPopulating = false;
                });
            }, () -> logger.warn(String.format(
                    "Could not find the web client corresponding to %s. Impossible to get the children of this plate (%s).",
                    webServerURI,
                    this
            )));
        }
    }
}
