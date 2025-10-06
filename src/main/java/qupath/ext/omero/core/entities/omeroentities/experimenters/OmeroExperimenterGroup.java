package qupath.ext.omero.core.entities.omeroentities.experimenters;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.omeroentities.OmeroDetails;
import qupath.ext.omero.core.entities.permissions.PermissionLevel;

import java.util.Objects;

/**
 * An OMERO experimenter as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#ExperimenterGroup">OME specifications</a>.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param type a link to the specifications of this object ({@link #TYPE} is expected). Optional
 * @param id the ID of the group. Required
 * @param omeroDetails details about this group. Required
 * @param name the name of the group. Optional
 * @param experimentersUrl a link to get all experimenters belonging to this group. Required
 */
public record OmeroExperimenterGroup(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") Long id,
        @SerializedName(value = "omero:details:") OmeroDetails omeroDetails,
        @SerializedName(value = "Name") String name,
        @SerializedName(value = "url:experimenters") String experimentersUrl
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#ExperimenterGroup";
    private static final Logger logger = LoggerFactory.getLogger(OmeroExperimenterGroup.class);

    public OmeroExperimenterGroup {
        Objects.requireNonNull(id);
        Objects.requireNonNull(omeroDetails);
        Objects.requireNonNull(experimentersUrl);

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent an experimenter group",
                    type,
                    TYPE
            );
        }
    }

    /**
     * Get the {@link PermissionLevel} of this group.
     *
     * @return a {@link PermissionLevel} corresponding to this group
     */
    public PermissionLevel getPermissionLevel() {
        return omeroDetails.permissions().getPermissionLevel();
    }
}
