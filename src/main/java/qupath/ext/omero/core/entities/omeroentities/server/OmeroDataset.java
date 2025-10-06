package qupath.ext.omero.core.entities.omeroentities.server;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.omeroentities.OmeroDetails;
import qupath.ext.omero.core.entities.permissions.Experimenter;
import qupath.ext.omero.core.entities.permissions.ExperimenterGroup;

import java.util.Objects;
import java.util.Optional;

/**
 * An OMERO dataset as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#Dataset">OME specifications</a>.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param type a link to the specifications of this object ({@link #TYPE} is expected). Optional
 * @param id the ID of the dataset. Required
 * @param name the name of the dataset. Optional
 * @param description the description of the dataset. Optional
 * @param childCount the number of children of the dataset. Required
 * @param omeroDetails details about this group. Optional
 */
public record OmeroDataset(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") Long id,
        @SerializedName(value = "Name") String name,
        @SerializedName(value = "Description") String description,
        @SerializedName(value = "omero:childCount") Integer childCount,
        @SerializedName(value = "omero:details:") OmeroDetails omeroDetails
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Dataset";
    private static final Logger logger = LoggerFactory.getLogger(OmeroDataset.class);

    public OmeroDataset {
        Objects.requireNonNull(id);
        Objects.requireNonNull(childCount);

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent a dataset",
                    type,
                    TYPE
            );
        }
    }

    /**
     * Get the experimenter owning this dataset.
     *
     * @return the experimenter owning this dataset, or an empty Optional if not found
     */
    public Optional<Experimenter> experimenter() {
        return omeroDetails == null ? Optional.empty() : Optional.ofNullable(omeroDetails.experimenter());
    }

    /**
     * Get the experimenter group owning this dataset.
     *
     * @return the experimenter group owning this dataset, or an empty Optional if not found
     */
    public Optional<ExperimenterGroup> group() {
        return omeroDetails == null ? Optional.empty() : Optional.ofNullable(omeroDetails.group());
    }
}
