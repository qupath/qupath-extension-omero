package qupath.ext.omero.core.entities.repositoryentities.serverentities;

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

import java.net.URI;
import java.util.ResourceBundle;

/**
 * Represents an OMERO screen.
 * A screen contains {@link Plate plates}.
 */
public class Screen extends ServerEntity {

    private static final Logger logger = LoggerFactory.getLogger(Screen.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Web.Entities.Screen.name"),
            resources.getString("Web.Entities.Screen.id"),
            resources.getString("Web.Entities.Screen.description"),
            resources.getString("Web.Entities.Screen.owner"),
            resources.getString("Web.Entities.Screen.group"),
            resources.getString("Web.Entities.Screen.nbPlates")
    };
    private final transient ObservableList<Plate> children = FXCollections.observableArrayList();
    private final transient ObservableList<Plate> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private transient boolean childrenPopulated = false;
    private transient URI webServerURI;
    private transient boolean isPopulating = false;
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
     * @throws IllegalStateException when the web server URI has not been set (see {@link #setWebServerURI(URI)})
     */
    @Override
    public ObservableList<? extends RepositoryEntity> getChildren() {
        if (!childrenPopulated) {
            populateChildren();
            childrenPopulated = true;
        }
        return childrenImmutable;
    }

    @Override
    public ReadOnlyStringProperty getLabel() {
        String name = this.name == null ? "" : this.name;
        return new SimpleStringProperty(name + " (" + childCount + ")");
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
            case 4 -> getGroup().getName();
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
     * @param type  the OMERO entity type
     * @return whether this type refers to a screen
     */
    public static boolean isScreen(String type) {
        return "http://www.openmicroscopy.org/Schemas/OME/2016-06#Screen".equalsIgnoreCase(type) || "Screen".equalsIgnoreCase(type);
    }

    /**
     * Set the web server URI of the server owning this screen. This is needed to populate its children.
     *
     * @param webServerURI the web server URI of this server
     */
    public void setWebServerURI(URI webServerURI) {
        this.webServerURI = webServerURI;
    }

    private void populateChildren() {
        if (webServerURI == null) {
            throw new IllegalStateException(
                    "The web server URI has not been set on this screen. See the setWebServerURI(URI) function."
            );
        } else {
            WebClients.getClientFromURI(webServerURI).ifPresentOrElse(client -> {
                isPopulating = true;
                client.getApisHandler().getPlates(getId()).thenAccept(plates -> {
                    children.addAll(plates);
                    isPopulating = false;
                });
            }, () -> logger.warn(String.format(
                    "Could not find the web client corresponding to %s. Impossible to get the children of this screen (%s).",
                    webServerURI,
                    this
            )));
        }
    }
}
