package qupath.ext.omero.core.apis.commonentities.shapes;

import com.google.gson.Gson;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroPoint;
import qupath.ext.omero.core.apis.json.permissions.Experimenter;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;
import java.util.Objects;

/**
 * A point.
 */
public class Point extends Shape {

    private static final Gson gson = new Gson();
    private final double x;
    private final double y;

    /**
     * Create a point from an {@link OmeroPoint}.
     *
     * @param omeroPoint the OMERO point to create the point from
     * @param roiId in OMERO, a ROI contains one or more shapes. This parameter is the ID of the ROI containing this shape
     * @throws NullPointerException if the provided OMERO point is null
     */
    public Point(OmeroPoint omeroPoint, long roiId) {
        super(
                omeroPoint.id(),
                roiId,
                omeroPoint.text(),
                omeroPoint.fillColor() == null ? null : rgbaToColor(omeroPoint.fillColor()),
                omeroPoint.strokeColor() == null ? null : rgbaToColor(omeroPoint.strokeColor()),
                omeroPoint.locked(),
                omeroPoint.c(),
                omeroPoint.z(),
                omeroPoint.t(),
                omeroPoint.omeroDetails() == null || omeroPoint.omeroDetails().experimenter() == null ?
                        null :
                        new Experimenter(omeroPoint.omeroDetails().experimenter())
        );

        this.x = omeroPoint.x();
        this.y = omeroPoint.y();
    }

    private Point(PathObject pathObject, boolean fillColor, double x, double y) {
        super(pathObject, fillColor);

        this.x = x;
        this.y = y;
    }

    /**
     * Create a list of points from a {@link PathObject}.
     *
     * @param pathObject the path object {@link PathObject} to create the points from
     * @param fillColor whether to fill the points with colors
     * @return a list of points corresponding to the path object
     * @throws NullPointerException if the provided path object is null or doesn't have a {@link ROI}
     */
    public static List<Point> create(PathObject pathObject, boolean fillColor) {
        return pathObject.getROI().getAllPoints().stream()
                .map(point2 -> new Point(pathObject, fillColor, point2.getX(), point2.getY()))
                .toList();
    }

    @Override
    public String createJson() {
        return gson.toJson(new OmeroPoint(
                getId(),
                OmeroPoint.TYPE,
                getText(),
                getFillColor().map(Shape::colorToRgba).orElse(null),
                getStrokeColor().map(Shape::colorToRgba).orElse(null),
                getLocked(),
                getPlane().getC(),
                getPlane().getZ(),
                getPlane().getT(),
                x,
                y,
                null
        ));
    }

    @Override
    protected ROI createRoi() {
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
        return point.getId() == this.getId() &&
                point.x == this.x && point.y == this.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), x, y);
    }
}
