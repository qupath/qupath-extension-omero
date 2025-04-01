package qupath.ext.omero.core.entities.shapes;

import com.google.gson.annotations.SerializedName;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Objects;

/**
 * A set of lines.
 */
public class Polyline extends Shape {

    private static final String TYPE = "Polyline";
    @SerializedName(value = "Points", alternate = "points") private final String pointString;

    /**
     * Creates a polyline corresponding to a path object.
     *
     * @param pathObject the path object corresponding to this shape
     * @param fillColor whether to fill the polyline with colors
     */
    public Polyline(PathObject pathObject, boolean fillColor) {
        super(TYPE, pathObject, fillColor);

        this.pointString = pointsToString(pathObject.getROI().getAllPoints());
    }

    @Override
    public ROI createRoi() {
        return ROIs.createPolylineROI(parseStringPoints(pointString == null ? "" : pointString), getPlane());
    }

    @Override
    public String toString() {
        return String.format("Polyline of points %s", pointString);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Polyline polyline))
            return false;
        return parseStringPoints(polyline.pointString == null ? "" : polyline.pointString)
                .equals(parseStringPoints(pointString == null ? "" : pointString));
    }

    @Override
    public int hashCode() {
        return Objects.hash(pointString);
    }

    /**
     * Indicate whether the provided shape type refers to a polyline.
     *
     * @param type the type of the shape according to the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06">Open Microscopy Environment OME Schema</a>
     * @return whether the provided shape type refers to a polyline
     */
    public static boolean isPolyline(String type) {
        return type.contains(TYPE);
    }
}
