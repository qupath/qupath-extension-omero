package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroImage;

/**
 * An OMERO well sample as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#WellSample">OME specifications</a>.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param type a link to the specifications of this object ({@link #TYPE} is expected). Optional
 * @param image the image represented by this well sample. Optional
 * @param plateAcquisition the plate acquisition represented by this well sample. Optional
 */
public record OmeroWellSample(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "Image") OmeroImage image,
        @SerializedName(value = "PlateAcquisition") OmeroPlateAcquisition plateAcquisition
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#WellSample";
    private static final Logger logger = LoggerFactory.getLogger(OmeroWellSample.class);

    public OmeroWellSample {
        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent a well sample",
                    type,
                    TYPE
            );
        }
    }
}
