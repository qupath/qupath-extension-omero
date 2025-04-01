package qupath.ext.omero.core.entities.shapes;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Objects;

/**
 * A text placed at some point.
 * This is not supported by the extension, which will creates a point instead.
 */
public class Label extends Shape {

    private static final Logger logger = LoggerFactory.getLogger(Label.class);
    private static final String TYPE = "Label";
    @SerializedName(value = "X", alternate = "x") private double x;
    @SerializedName(value = "Y", alternate = "y") private double y;

    private Label(String type, PathObject pathObject, boolean fillColor) {
        super(type, pathObject, fillColor);
    }

    @Override
    public ROI createRoi() {
        logger.warn("Creating point (requested label shape is unsupported)");
        return ROIs.createPointsROI(x, y, getPlane());
    }

    @Override
    public String toString() {
        return String.format("Label located at {x: %f, y: %f}", x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Label label))
            return false;
        return label.x == this.x && label.y == this.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    /**
     * Indicate whether the provided shape type refers to a label.
     *
     * @param type the type of the shape according to the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06">Open Microscopy Environment OME Schema</a>
     * @return whether the provided shape type refers to a label
     */
    public static boolean isLabel(String type) {
        return type.contains(TYPE);
    }
}
