package qupath.ext.omero.core.entities.shapes2.omeroshapes;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public record OmeroEllipse(
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
        @SerializedName(value = "RadiusX") Double radiusX,
        @SerializedName(value = "RadiusY") Double radiusY
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Ellipse";
    private static final Logger logger = LoggerFactory.getLogger(OmeroEllipse.class);

    public OmeroEllipse {
        Objects.requireNonNull(id);
        Objects.requireNonNull(x);
        Objects.requireNonNull(y);
        Objects.requireNonNull(radiusX);
        Objects.requireNonNull(radiusY);

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent an ellipse",
                    type,
                    TYPE
            );
        }
    }
}
