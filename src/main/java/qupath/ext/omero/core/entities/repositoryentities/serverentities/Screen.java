package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import com.google.gson.annotations.SerializedName;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;

import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an OMERO screen.
 * A screen contains {@link Plate plates}.
 */
public class Screen extends ServerEntity {

    private static final Logger logger = LoggerFactory.getLogger(Screen.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Entities.Screen.name"),
            resources.getString("Entities.Screen.id"),
            resources.getString("Entities.Screen.description"),
            resources.getString("Entities.Screen.owner"),
            resources.getString("Entities.Screen.group"),
            resources.getString("Entities.Screen.nbPlates")
    };
    private final transient ObservableList<Plate> children = FXCollections.observableArrayList();
    private final transient ObservableList<Plate> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final transient AtomicBoolean childrenPopulated = new AtomicBoolean(false);
    private transient volatile boolean isPopulating = false;
    @SerializedName(value = "Description") private String description;
    @SerializedName(value = "omero:childCount") private int childCount;

    /**
     * Creates an empty screen.
     */
    public Screen() {
        // This constructor is declared because otherwise transient fields
        // of this class are not declared when it is created through JSON
    }

    /**
     * Creates an empty screen only defined by its ID.
     */
    public Screen(long id) {
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
        return String.format("%s (%d)", name == null ? String.format("Screen %d", id) : name, childCount);
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
        return String.format("Screen %s of ID %d", name, id);
    }

    /**
     * Indicates if an OMERO entity type refers to a screen
     *
     * @param type the OMERO entity type
     * @return whether this type refers to a screen
     */
    public static boolean isScreen(String type) {
        return "http://www.openmicroscopy.org/Schemas/OME/2016-06#Screen".equalsIgnoreCase(type) || "Screen".equalsIgnoreCase(type);
    }

    private void populateChildren() {
        if (webServerURI == null) {
            throw new IllegalStateException("The web server URI has not been set on this screen. Cannot populate children");
        }

        Client.getClientFromURI(webServerURI).ifPresentOrElse(client -> {
            isPopulating = true;

            client.getApisHandler().getPlates(id).whenComplete((plates, error) -> {
                isPopulating = false;

                if (plates == null) {
                    logger.error("Error while retrieving children plates of {}", this, error);
                    return;
                }

                logger.debug("Got plates {} as children of {}", plates, this);
                children.addAll(plates);
            });
        }, () -> logger.warn(
                "Could not find the web client corresponding to {}. Impossible to get the children of this screen ({}).",
                webServerURI,
                this
        ));
    }
}
