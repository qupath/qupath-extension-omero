package qupath.ext.omero.core.entities.shapes;

import com.google.gson.annotations.SerializedName;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;
import java.util.Objects;

/**
 * A point.
 */
public class Point extends Shape {

    private static final String TYPE = "Point";
    @SerializedName(value = "X", alternate = "x") private final double x;
    @SerializedName(value = "Y", alternate = "y") private final double y;

    private Point(double x, double y, PathObject pathObject, boolean fillColor) {
        super(TYPE, pathObject, fillColor);

        this.x = x;
        this.y = y;
    }

    /**
     * Create a list of points corresponding to a path object.
     *
     * @param pathObject the path object corresponding to this shape
     * @param fillColor whether to fill the points with colors
     * @return a list of points corresponding to the path object
     */
    public static List<Point> create(PathObject pathObject, boolean fillColor) {
        return pathObject.getROI().getAllPoints().stream()
                .map(point2 -> new Point(point2.getX(), point2.getY(), pathObject, fillColor))
                .toList();
    }

    @Override
    public ROI createRoi() {
        return ROIs.createPointsROI(x, y, getPlane());
    }

    @Override
    public String toString() {
        return String.format("Point located at {x: %f, y: %f}", x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Point point))
            return false;
        return point.x == this.x && point.y == this.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    /**
     * Indicate whether the provided shape type refers to a point.
     *
     * @param type the type of the shape according to the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06">Open Microscopy Environment OME Schema</a>
     * @return whether the provided shape type refers to a point
     */
    public static boolean isPoint(String type) {
        return type.contains(TYPE);
    }
}
