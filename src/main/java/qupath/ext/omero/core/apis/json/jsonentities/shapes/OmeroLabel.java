package qupath.ext.omero.core.apis.json.jsonentities.shapes;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;

import java.util.Objects;

/**
 * An OMERO label as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#Label">OME specifications</a>.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param id the ID of the shape. Required
 * @param type a link to the specifications of this object ({@link #TYPE} is expected). Optional
 * @param text a text describing the shape. Optional
 * @param fillColor the fill color of the shape with the RGBA format. Optional
 * @param strokeColor the stroke color of the shape with the RGBA format. Optional
 * @param locked whether the shape is locked. Optional
 * @param c the 0-based channel index of the shape. Optional
 * @param z the 0-based z-stack index of the shape. Optional
 * @param t the 0-based timepoint index of the shape. Optional
 * @param x the X coordinate of the center of the label. Required
 * @param y the Y coordinate of the center of the label. Required
 * @param omeroDetails details about this shape. Optional
 */
public record OmeroLabel(
        @SerializedName(value = "@id") Long id,
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "Text") String text,
        @SerializedName(value = "FillColor") Integer fillColor,
        @SerializedName(value = "StrokeColor") Integer strokeColor,
        @SerializedName(value = "Locked") Boolean locked,
        @SerializedName(value = "TheC") Integer c,
        @SerializedName(value = "TheZ") Integer z,
        @SerializedName(value = "TheT") Integer t,
        @SerializedName(value = "X") Double x,
        @SerializedName(value = "Y") Double y,
        @SerializedName(value = "omero:details:") OmeroDetails omeroDetails
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Label";
    private static final Logger logger = LoggerFactory.getLogger(OmeroLabel.class);

    public OmeroLabel {
        Objects.requireNonNull(id);
        Objects.requireNonNull(x);
        Objects.requireNonNull(y);

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent a label",
                    type,
                    TYPE
            );
        }
    }
}
