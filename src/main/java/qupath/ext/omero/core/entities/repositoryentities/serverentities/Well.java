package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;

import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Represents an OMERO well.
 * A well acquisition contains {@link Image} ids.
 */
public class Well extends ServerEntity {

    private static final Logger logger = LoggerFactory.getLogger(Well.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final Gson gson = new Gson()
;    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Entities.Well.name"),
            resources.getString("Entities.Well.id"),
            resources.getString("Entities.Well.owner"),
            resources.getString("Entities.Well.group"),
            resources.getString("Entities.Well.column"),
            resources.getString("Entities.Well.row")
    };
    @SerializedName(value = "WellSamples") private List<WellSample> wellSamples;
    @SerializedName(value = "Column") private int column;
    @SerializedName(value = "Row") private int row;
    private transient ObservableList<ServerEntity> children;
    private record WellSample(
            @SerializedName(value = "Image") Image image,
            @SerializedName(value = "PlateAcquisition") PlateAcquisition plateAcquisition
    ) {}

    /**
     * Creates an empty well only defined by its ID.
     */
    public Well(long id) {
        this.id = id;
    }

    @Override
    public boolean hasChildren() {
        return !getChildren().isEmpty();
    }

    @Override
    public synchronized ObservableList<? extends RepositoryEntity> getChildren() {
        if (children == null) {
            children = FXCollections.unmodifiableObservableList(wellSamples == null ?
                    FXCollections.emptyObservableList() :
                    FXCollections.observableList(wellSamples.stream()
                            .map(WellSample::image)
                            .map(image -> {
                                if (image == null) {
                                    logger.debug("Cannot create image from well sample in well {} because the image field is null", this);
                                    return null;
                                }

                                try {
                                    // Serialize and deserialize image because every server entity should be created with ServerEntity.createFromJsonElement()
                                    return ServerEntity.createFromJsonElement(gson.toJsonTree(image), webServerURI);
                                } catch (JsonSyntaxException e) {
                                    logger.debug("Cannot create image {} from well sample in well {}", image, this, e);
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .toList()
                    )
            );
        }

        return children;
    }

    @Override
    public String getLabel() {
        return String.format("%s%d", (char) ('A' + row), column+1);
    }

    @Override
    public boolean isPopulatingChildren() {
        return false;
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
     * Retrieve a list of image ids belonging to this well.
     *
     * @param withPlateAcquisition whether to include image that are part of a plate acquisition
     * @return the image ids belonging to this well
     */
    public List<Long> getImagesIds(boolean withPlateAcquisition) {
        return wellSamples == null ? List.of() : wellSamples.stream()
                .filter(wellSample -> {
                    if (withPlateAcquisition) {
                        return wellSample.plateAcquisition() != null;
                    } else {
                        return wellSample.plateAcquisition() == null;
                    }
                })
                .map(WellSample::image)
                .filter(Objects::nonNull)
                .map(Image::getId)
                .toList();
    }
}
