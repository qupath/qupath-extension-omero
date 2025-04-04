package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import com.google.gson.annotations.SerializedName;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.repositoryentities.OrphanedFolder;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;

import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an OMERO dataset.
 * A dataset contains images (described in {@link qupath.ext.omero.core.entities.repositoryentities.serverentities.image image}),
 * and is a child of a {@link Project} or an {@link OrphanedFolder OrphanedFolder}.
 */
public class Dataset extends ServerEntity {

    private static final Logger logger = LoggerFactory.getLogger(Dataset.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Entities.Dataset.name"),
            resources.getString("Entities.Dataset.id"),
            resources.getString("Entities.Dataset.description"),
            resources.getString("Entities.Dataset.owner"),
            resources.getString("Entities.Dataset.group"),
            resources.getString("Entities.Dataset.nbImages")
    };
    private final transient ObservableList<Image> children = FXCollections.observableArrayList();
    private final transient ObservableList<Image> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final transient AtomicBoolean childrenPopulated = new AtomicBoolean(false);
    private transient volatile boolean isPopulating = false;
    @SerializedName(value = "Description") private String description;
    @SerializedName(value = "omero:childCount") private int childCount;

    /**
     * Creates an empty dataset.
     */
    public Dataset() {
        // This constructor is declared because otherwise transient fields
        // of this class are not declared when it is created through JSON
    }

    /**
     * Creates an empty dataset only defined by its ID.
     */
    public Dataset(long id) {
        this.id = id;
    }

    @Override
    public boolean hasChildren() {
        return childCount > 0;
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
        return String.format("%s (%d)", name == null ? String.format("Dataset %d", id) : name, childCount);
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
            case 2 -> description == null || description.isEmpty() ? "-" : description;
            case 3 -> getOwner().getFullName();
            case 4 -> getGroupName();
            case 5 -> String.valueOf(childCount);
            default -> "";
        };
    }

    @Override
    public int getNumberOfAttributes() {
        return ATTRIBUTES.length;
    }

    @Override
    public String toString() {
        return String.format("Dataset %s of ID %d", name, id);
    }

    /**
     * Indicates if an OMERO entity type refers to a dataset
     *
     * @param type the OMERO entity type
     * @return whether this type refers to a dataset
     */
    public static boolean isDataset(String type) {
        return "http://www.openmicroscopy.org/Schemas/OME/2016-06#Dataset".equalsIgnoreCase(type) || "Dataset".equalsIgnoreCase(type);
    }

    private void populateChildren() {
        if (webServerURI == null) {
            throw new IllegalStateException("The web server URI has not been set on this dataset. Cannot populate children");
        }

        Client.getClientFromURI(webServerURI).ifPresentOrElse(client -> {
            isPopulating = true;

            client.getApisHandler().getImages(id).whenComplete((images, error) -> {
                isPopulating = false;

                if (images == null) {
                    logger.error("Error while retrieving children images of {}", this, error);
                    return;
                }

                logger.debug("Got images {} as children of {}", images, this);
                children.addAll(images);
            });
        }, () -> logger.warn(
                "Could not find the web client corresponding to {}. Impossible to get the children of this dataset ({}).",
                webServerURI,
                this
        ));
    }
}
