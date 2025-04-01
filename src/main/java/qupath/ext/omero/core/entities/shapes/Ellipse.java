package qupath.ext.omero.core.entities.shapes;

import com.google.gson.annotations.SerializedName;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Objects;

/**
 * An ellipse.
 */
public class Ellipse extends Shape {

    private static final String TYPE = "Ellipse";
    @SerializedName(value = "X", alternate = "x") private final double x;
    @SerializedName(value = "Y", alternate = "y") private final double y;
    @SerializedName(value = "RadiusX", alternate = "radiusX") private final double radiusX;
    @SerializedName(value = "RadiusY", alternate = "radiusY") private final double radiusY;

    /**
     * Creates an ellipse corresponding to a path object.
     *
     * @param pathObject the path object corresponding to this shape
     * @param fillColor whether to fill the ellipse with colors
     */
    public Ellipse(PathObject pathObject, boolean fillColor) {
        super(TYPE, pathObject, fillColor);

        this.x = pathObject.getROI().getCentroidX();
        this.y = pathObject.getROI().getCentroidY();
        this.radiusX = pathObject.getROI().getBoundsWidth()/2;
        this.radiusY = pathObject.getROI().getBoundsHeight()/2;
    }

    @Override
    public ROI createRoi() {
        return ROIs.createEllipseROI(x-radiusX, y-radiusY, radiusX*2, radiusY*2, getPlane());
    }

    @Override
    public String toString() {
        return String.format("Ellipse located centered at {x: %f, y: %f} of radius {x: %f, y: %f}", x, y, radiusX, radiusY);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Ellipse ellipse))
            return false;
        return ellipse.x == this.x && ellipse.y == this.y && ellipse.radiusX == this.radiusX && ellipse.radiusY == this.radiusY;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, radiusX, radiusY);
    }

    /**
     * Indicate whether the provided shape type refers to an ellipse.
     *
     * @param type the type of the shape according to the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06">Open Microscopy Environment OME Schema</a>
     * @return whether the provided shape type refers to an ellipse
     */
    public static boolean isEllipse(String type) {
        return type.contains(TYPE);
    }
}
