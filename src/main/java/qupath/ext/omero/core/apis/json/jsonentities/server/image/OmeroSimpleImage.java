package qupath.ext.omero.core.apis.json.jsonentities.server.image;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * An OMERO image as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#Image">OME specifications</a>
 * but only defined by its ID.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param type a link to the specifications of this object ({@link #TYPE} is expected). Optional
 * @param id the ID of the image. Required
 */
public record OmeroSimpleImage(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") Long id
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Image";
    private static final Logger logger = LoggerFactory.getLogger(OmeroSimpleImage.class);

    public OmeroSimpleImage {
        Objects.requireNonNull(id, "@id not provided");

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent an image",
                    type,
                    TYPE
            );
        }
    }
}
