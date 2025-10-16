package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;

import java.util.Objects;
import java.util.Optional;

/**
 * An OMERO screen as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#Screen">OME specifications</a>.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param type a link to the specifications of this object ({@link #TYPE} is expected). Optional
 * @param id the ID of the screen. Required
 * @param name the name of the screen. Optional
 * @param description the description of the screen. Optional
 * @param childCount the number of children of the screen. Required
 * @param omeroDetails details about this group. Optional
 */
public record OmeroScreen(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") Long id,
        @SerializedName(value = "Name") String name,
        @SerializedName(value = "Description") String description,
        @SerializedName(value = "omero:childCount") Integer childCount,
        @SerializedName(value = "omero:details") OmeroDetails omeroDetails
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Screen";
    private static final Logger logger = LoggerFactory.getLogger(OmeroScreen.class);

    public OmeroScreen {
        Objects.requireNonNull(id, "@id not provided");
        Objects.requireNonNull(childCount, "omero:childCount not provided");

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent a screen",
                    type,
                    TYPE
            );
        }
    }

    /**
     * Get the experimenter owning this screen.
     *
     * @return the experimenter owning this screen, or an empty Optional if not found
     */
    public Optional<OmeroExperimenter> owner() {
        return omeroDetails == null ? Optional.empty() : Optional.ofNullable(omeroDetails.experimenter());
    }

    /**
     * Get the experimenter group owning this screen.
     *
     * @return the experimenter group owning this screen, or an empty Optional if not found
     */
    public Optional<OmeroExperimenterGroup> group() {
        return omeroDetails == null ? Optional.empty() : Optional.ofNullable(omeroDetails.group());
    }
}
