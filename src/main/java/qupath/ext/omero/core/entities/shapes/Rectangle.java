package qupath.ext.omero.core.entities.shapes;

import com.google.gson.annotations.SerializedName;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Objects;

/**
 * A rectangle.
 */
public class Rectangle extends Shape {

    private static final String TYPE = "Rectangle";
    @SerializedName(value = "X", alternate = "x") private final double x;
    @SerializedName(value = "Y", alternate = "y") private final double y;
    @SerializedName(value = "Width", alternate = "width") private final double width;
    @SerializedName(value = "Height", alternate = "height") private final double height;

    /**
     * Creates a rectangle corresponding to a path object.
     *
     * @param pathObject the path object corresponding to this shape
     * @param fillColor whether to fill the rectangle with colors
     */
    public Rectangle(PathObject pathObject, boolean fillColor) {
        super(TYPE, pathObject, fillColor);

        this.x = pathObject.getROI().getBoundsX();
        this.y = pathObject.getROI().getBoundsY();
        this.width = pathObject.getROI().getBoundsWidth();
        this.height = pathObject.getROI().getBoundsHeight();
    }

    @Override
    public ROI createRoi() {
        return ROIs.createRectangleROI(x, y, width, height, getPlane());
    }

    @Override
    public String toString() {
        return String.format("Rectangle located (top left) at {x: %f, y: %f} of width %f and height %f", x, y, width, height);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Rectangle rectangle))
            return false;
        return rectangle.x == this.x && rectangle.y == this.y && rectangle.width == this.width && rectangle.height == this.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height);
    }

    /**
     * Indicate whether the provided shape type refers to a rectangle.
     *
     * @param type the type of the shape according to the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06">Open Microscopy Environment OME Schema</a>
     * @return whether the provided shape type refers to a rectangle
     */
    public static boolean isRectangle(String type) {
        return type.contains(TYPE);
    }
}
