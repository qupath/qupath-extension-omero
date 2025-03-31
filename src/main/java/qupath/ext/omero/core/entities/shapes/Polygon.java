package qupath.ext.omero.core.entities.shapes;

import com.google.gson.annotations.SerializedName;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Objects;

/**
 * A polygon.
 */
public class Polygon extends Shape {

    private static final String TYPE = "Polygon";
    @SerializedName(value = "Points", alternate = "points") private final String pointString;

    /**
     * Creates a polygon corresponding to a path object.
     *
     * @param pathObject the path object corresponding to this shape
     * @param roi the roi describing the polygon
     * @param fillColor whether to fill the polygon with colors
     */
    public Polygon(PathObject pathObject, ROI roi, boolean fillColor) {
        super(TYPE, pathObject, fillColor);

        this.pointString = pointsToString(roi.getAllPoints());
    }

    @Override
    public ROI createRoi() {
        return ROIs.createPolygonROI(parseStringPoints(pointString == null ? "" : pointString), getPlane());
    }

    @Override
    public String toString() {
        return String.format("Polygon of points %s", pointString);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Polygon polygon))
            return false;
        return parseStringPoints(polygon.pointString == null ? "" : polygon.pointString)
                .equals(parseStringPoints(pointString == null ? "" : pointString));
    }

    @Override
    public int hashCode() {
        return Objects.hash(pointString);
    }

    /**
     * Indicate whether the provided shape type refers to a polygon.
     *
     * @param type the type of the shape according to the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06">Open Microscopy Environment OME Schema</a>
     * @return whether the provided shape type refers to a polygon
     */
    public static boolean isPolygon(String type) {
        return type.contains(TYPE);
    }
}
