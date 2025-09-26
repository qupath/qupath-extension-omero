package qupath.ext.omero.core.entities.repositoryentities2.serverentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities2.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroDataset;

import java.net.URI;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class Dataset extends ServerEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Entities.Dataset.name"),
            resources.getString("Entities.Dataset.id"),
            resources.getString("Entities.Dataset.description"),
            resources.getString("Entities.Dataset.owner"),
            resources.getString("Entities.Dataset.group"),
            resources.getString("Entities.Dataset.nbImages")
    };
    private final String name;
    private final Owner owner;
    private final Group group;
    private final String description;
    private final int childCount;

    public Dataset(OmeroDataset omeroDataset, URI webServerUri) {
        super(omeroDataset.id(), webServerUri);

        this.name = omeroDataset.name();
        this.owner = omeroDataset.owner();
        this.group = omeroDataset.group();
        this.description = omeroDataset.description();
        this.childCount = omeroDataset.childCount();
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
            case 1 -> String.valueOf(id);
            case 2 -> description == null || description.isEmpty() ? "-" : description;
            case 3 -> owner == null || owner.getFullName().isEmpty() ? "-" : owner.getFullName();
            case 4 -> group == null || group.getName().isEmpty() ? "-" : group.getName();
            case 5 -> String.valueOf(childCount);
            default -> "";
        };
    }

    @Override
    public int getNumberOfAttributes() {
        return ATTRIBUTES.length;
    }

    @Override
    public boolean hasChildren() {
        return childCount > 0;
    }

    @Override
    public CompletableFuture<List<? extends RepositoryEntity>> getChildren(Owner owner, Group group) {
        //TODO: get images of dataset with group and owner

        return null;
    }

    @Override
    public String getLabel() {
        return String.format("%s (%d)", name == null ? String.format("Dataset %d", id) : name, childCount);
    }

    public static boolean isDataset(String type) {
        return "http://www.openmicroscopy.org/Schemas/OME/2016-06#Dataset".equalsIgnoreCase(type) || "Dataset".equalsIgnoreCase(type);
    }
}
