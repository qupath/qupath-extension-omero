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
 * An OMERO plate as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#Plate">OME specifications</a>.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param type a link to the specifications of this object ({@link #TYPE} is expected). Optional
 * @param id the ID of the plate. Required
 * @param name the name of the plate. Optional
 * @param columns the number of columns of the plate. Optional, default to 0
 * @param rows the number of rows of the plate. Optional, default to 0
 * @param omeroDetails details about this group. Optional
 */
public record OmeroPlate(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") Long id,
        @SerializedName(value = "Name") String name,
        @SerializedName(value = "Columns") int columns,
        @SerializedName(value = "Rows") int rows,
        @SerializedName(value = "omero:details") OmeroDetails omeroDetails
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Plate";
    private static final Logger logger = LoggerFactory.getLogger(OmeroPlate.class);

    public OmeroPlate {
        Objects.requireNonNull(id, "@id not provided");

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent a plate",
                    type,
                    TYPE
            );
        }
    }

    /**
     * Get the experimenter owning this plate.
     *
     * @return the experimenter owning this plate, or an empty Optional if not found
     */
    public Optional<OmeroExperimenter> owner() {
        return omeroDetails == null ? Optional.empty() : Optional.ofNullable(omeroDetails.experimenter());
    }

    /**
     * Get the experimenter group owning this plate.
     *
     * @return the experimenter group owning this plate, or an empty Optional if not found
     */
    public Optional<OmeroExperimenterGroup> group() {
        return omeroDetails == null ? Optional.empty() : Optional.ofNullable(omeroDetails.group());
    }
}
