package qupath.ext.omero.core.apis.commonentities.shapes;

import com.google.gson.Gson;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroPolyline;
import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;
import java.util.Objects;

/**
 * A collection of lines.
 */
public class Polyline extends Shape {

    private static final Gson gson = new Gson();
    private final List<Point2> points;

    /**
     * Create a polyline from an {@link OmeroPolyline}.
     *
     * @param omeroPolyline the OMERO polyline to create the polyline from
     * @param roiId in OMERO, a ROI contains one or more shapes. This parameter is the ID of the ROI containing this shape
     * @throws NullPointerException if the provided OMERO polyline is null
     */
    public Polyline(OmeroPolyline omeroPolyline, long roiId) {
        super(
                omeroPolyline.id(),
                roiId,
                omeroPolyline.text(),
                omeroPolyline.fillColor() == null ? null : rgbaToColor(omeroPolyline.fillColor()),
                omeroPolyline.strokeColor() == null ? null : rgbaToColor(omeroPolyline.strokeColor()),
                omeroPolyline.locked() != null && omeroPolyline.locked(),
                omeroPolyline.c() == null ? 0 : omeroPolyline.c(),
                omeroPolyline.z() == null ? 0 : omeroPolyline.z(),
                omeroPolyline.t() == null ? 0 : omeroPolyline.t(),
                omeroPolyline.omeroDetails() == null || omeroPolyline.omeroDetails().experimenter() == null ?
                        null :
                        new SimpleEntity(
                                omeroPolyline.omeroDetails().experimenter().id(),
                                omeroPolyline.omeroDetails().experimenter().fullName()
                        )
        );

        this.points = parseStringPoints(omeroPolyline.points());
    }

    /**
     * Create a polyline from a {@link PathObject}.
     *
     * @param pathObject the {@link PathObject} to create the polyline from
     * @param fillColor whether this shape should have a fill color
     * @throws NullPointerException if the provided path object is null or doesn't have a {@link ROI}
     */
    public Polyline(PathObject pathObject, boolean fillColor) {
        super(pathObject, fillColor);

        this.points = pathObject.getROI().getAllPoints();
    }

    @Override
    public String createJson() {
        return gson.toJson(new OmeroPolyline(
                getId(),
                getOldId(),
                OmeroPolyline.TYPE,
                getText(),
                getFillColor().map(Shape::colorToRgba).orElse(null),
                getStrokeColor().map(Shape::colorToRgba).orElse(null),
                getLocked(),
                getPlane().getC(),
                getPlane().getZ(),
                getPlane().getT(),
                pointsToString(points),
                null
        ));
    }

    @Override
    protected ROI createRoi() {
        return ROIs.createPolylineROI(points, getPlane());
    }

    @Override
    public String toString() {
        return String.format("Polyline of ID %d and points %s", getId(), points);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Polyline polyline))
            return false;
        return polyline.points.equals(this.points);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), points);
    }
}
