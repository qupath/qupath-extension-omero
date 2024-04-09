package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import com.google.gson.annotations.SerializedName;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;

import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an OMERO project.
 * A project contains {@link Dataset Datasets}.
 */
public class Project extends ServerEntity {

    private static final Logger logger = LoggerFactory.getLogger(Project.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Web.Entities.Project.name"),
            resources.getString("Web.Entities.Project.id"),
            resources.getString("Web.Entities.Project.description"),
            resources.getString("Web.Entities.Project.owner"),
            resources.getString("Web.Entities.Project.group"),
            resources.getString("Web.Entities.Project.nbDatasets")
    };
    private final transient ObservableList<Dataset> children = FXCollections.observableArrayList();
    private final transient ObservableList<Dataset> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final transient AtomicBoolean childrenPopulated = new AtomicBoolean(false);
    private transient volatile boolean isPopulating = false;
    @SerializedName(value = "Description") private String description;
    @SerializedName(value = "omero:childCount") private int childCount;

    /**
     * Creates an empty project.
     */
    public Project() {
        // This constructor is declared because otherwise transient fields
        // of this class are not declared when it is created through JSON
    }

    /**
     * Creates an empty project only defined by its ID.
     */
    public Project(long id) {
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
        return String.format("Project %s of ID %d", name, id);
    }

    /**
     * Indicates if an OMERO entity type refers to a project
     *
     * @param type  the OMERO entity type
     * @return whether this type refers to a project
     */
    public static boolean isProject(String type) {
        return "http://www.openmicroscopy.org/Schemas/OME/2016-06#Project".equalsIgnoreCase(type) || "Project".equalsIgnoreCase(type);
    }

    private void populateChildren() {
        if (webServerURI == null) {
            throw new IllegalStateException(
                    "The web server URI has not been set on this project. See the setWebServerURI(URI) function."
            );
        } else {
            WebClients.getClientFromURI(webServerURI).ifPresentOrElse(client -> {
                isPopulating = true;
                client.getApisHandler().getDatasets(getId()).thenAccept(datasets -> {
                    children.addAll(datasets);
                    isPopulating = false;
                });
            }, () -> logger.warn(String.format(
                    "Could not find the web client corresponding to %s. Impossible to get the children of this project (%s).",
                    webServerURI,
                    this
            )));
        }
    }
}
