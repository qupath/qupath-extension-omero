package qupath.ext.omero.core.entities.shapes;

import com.google.gson.annotations.SerializedName;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Objects;

/**
 * A line.
 */
public class Line extends Shape {

    private static final String TYPE = "Line";
    @SerializedName(value = "X1", alternate = "x1") private final double x1;
    @SerializedName(value = "Y1", alternate = "y1") private final double y1;
    @SerializedName(value = "X2", alternate = "x2") private final double x2;
    @SerializedName(value = "Y2", alternate = "y2") private final double y2;

    /**
     * Creates a line corresponding to a path object.
     *
     * @param pathObject the path object corresponding to this shape
     * @param fillColor whether to fill the line with colors
     */
    public Line(PathObject pathObject, boolean fillColor) {
        super(TYPE, pathObject, fillColor);

        if (!(pathObject.getROI() instanceof LineROI lineRoi)) {
            throw new IllegalArgumentException(String.format("The provided path object %s doesn't have a line ROI. Can't create line", pathObject));
        }
        this.x1 = lineRoi.getX1();
        this.y1 = lineRoi.getY1();
        this.x2 = lineRoi.getX2();
        this.y2 = lineRoi.getY2();
    }

    @Override
    public ROI createRoi() {
        return ROIs.createLineROI(x1, y1, x2, y2, getPlane());
    }

    @Override
    public String toString() {
        return String.format("Line located between {x1: %f, y1: %f} and {x2: %f, y2: %f}", x1, y1, x2, y2);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Line line))
            return false;
        return line.x1 == this.x1 && line.y1 == this.y1 && line.x2 == this.x2 && line.y2 == this.y2;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x1, y1, x2, y2);
    }

    /**
     * Indicate whether the provided shape type refers to a line.
     *
     * @param type the type of the shape according to the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06">Open Microscopy Environment OME Schema</a>
     * @return whether the provided shape type refers to a line
     */
    public static boolean isLine(String type) {
        return type.contains(TYPE);
    }
}
