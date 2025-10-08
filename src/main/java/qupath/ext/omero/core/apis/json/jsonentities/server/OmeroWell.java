package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An OMERO well as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#Well">OME specifications</a>.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param type a link to the specifications of this object ({@link #TYPE} is expected). Optional
 * @param id the ID of the well. Required
 * @param name the name of the well. Optional
 * @param wellSamples the list of well samples of this well. Optional
 * @param column the column this well belongs to. Optional
 * @param row the row this well belongs to. Optional
 * @param omeroDetails details about this group. Optional
 */
public record OmeroWell(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") Long id,
        @SerializedName(value = "Name") String name,
        @SerializedName(value = "WellSamples") List<OmeroWellSample> wellSamples,
        @SerializedName(value = "Column") Integer column,
        @SerializedName(value = "Row") Integer row,
        @SerializedName(value = "omero:details:") OmeroDetails omeroDetails
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Well";
    private static final Logger logger = LoggerFactory.getLogger(OmeroWell.class);

    public OmeroWell {
        Objects.requireNonNull(id);

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent a well",
                    type,
                    TYPE
            );
        }
    }

    /**
     * Get the experimenter owning this well.
     *
     * @return the experimenter owning this well, or an empty Optional if not found
     */
    public Optional<OmeroExperimenter> owner() {
        return omeroDetails == null ? Optional.empty() : Optional.ofNullable(omeroDetails.experimenter());
    }

    /**
     * Get the experimenter group owning this well.
     *
     * @return the experimenter group owning this well, or an empty Optional if not found
     */
    public Optional<OmeroExperimenterGroup> group() {
        return omeroDetails == null ? Optional.empty() : Optional.ofNullable(omeroDetails.group());
    }
}
