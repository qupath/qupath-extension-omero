package qupath.ext.omero.core.apis.json.jsonentities.server.image;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroSimpleDetails;

import java.util.Objects;

/**
 * An OMERO image as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#Image">OME specifications</a>.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param type a link to the specifications of this object ({@link #TYPE} is expected). Optional
 * @param id the ID of the image. Required
 * @param name the name of the image. Optional
 * @param acquisitionDate the timestamp corresponding to the time when this image was acquired. Optional
 * @param pixels information on the image. Required
 * @param omeroDetails details about this group. Required
 */
public record OmeroImage(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") Long id,
        @SerializedName(value = "Name") String name,
        @SerializedName(value = "AcquisitionDate") Long acquisitionDate,
        @SerializedName(value = "Pixels") OmeroPixels pixels,
        @SerializedName(value = "omero:details") OmeroSimpleDetails omeroDetails
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Image";
    private static final Logger logger = LoggerFactory.getLogger(OmeroImage.class);

    public OmeroImage {
        Objects.requireNonNull(id, "@id not provided");
        Objects.requireNonNull(pixels, "Pixels not provided");
        Objects.requireNonNull(omeroDetails, "omero:details not provided");

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent an image",
                    type,
                    TYPE
            );
        }
    }
}
