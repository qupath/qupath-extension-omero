package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Represents an OMERO annotation containing a rating (from 0 to 5).
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param id the ID of the annotation. Required
 * @param namespace the namespace of the annotation. Optional
 * @param type a text describing this object ({@link #TYPE} is expected). Optional
 * @param owner the owner of this annotation. Optional
 * @param link a link containing the adder of this annotation. Optional
 * @param value the rating (from 0 to 5). Required
 */
public record OmeroRatingAnnotation(
        Long id,
        @SerializedName("ns") String namespace,
        @SerializedName("class") String type,
        OmeroAnnotationExperimenter owner,
        OmeroLink link,
        @SerializedName("longValue") Short value
) {
    public static final String TYPE = "LongAnnotationI";
    private static final Logger logger = LoggerFactory.getLogger(OmeroRatingAnnotation.class);

    public OmeroRatingAnnotation {
        Objects.requireNonNull(id, "id not provided");
        Objects.requireNonNull(value, "longValue not provided");

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent a rating annotation",
                    type,
                    TYPE
            );
        }
    }
}
