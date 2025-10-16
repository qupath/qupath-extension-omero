package qupath.ext.omero.core.apis.commonentities.shapes;

import com.google.gson.Gson;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroPolygon;
import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;
import java.util.Objects;

/**
 * A polygon.
 */
public class Polygon extends Shape {

    private static final Gson gson = new Gson();
    private final List<Point2> points;

    /**
     * Create a polygon from an {@link OmeroPolygon}.
     *
     * @param omeroPolygon the OMERO polygon to create the polygon from
     * @param roiId in OMERO, a ROI contains one or more shapes. This parameter is the ID of the ROI containing this shape
     * @throws NullPointerException if the provided OMERO polygon is null
     */
    public Polygon(OmeroPolygon omeroPolygon, long roiId) {
        super(
                omeroPolygon.id(),
                roiId,
                omeroPolygon.text(),
                omeroPolygon.fillColor() == null ? null : rgbaToColor(omeroPolygon.fillColor()),
                omeroPolygon.strokeColor() == null ? null : rgbaToColor(omeroPolygon.strokeColor()),
                omeroPolygon.locked(),
                omeroPolygon.c(),
                omeroPolygon.z(),
                omeroPolygon.t(),
                omeroPolygon.omeroDetails() == null || omeroPolygon.omeroDetails().experimenter() == null ?
                        null :
                        new SimpleEntity(
                                omeroPolygon.omeroDetails().experimenter().id(),
                                omeroPolygon.omeroDetails().experimenter().fullName()
                        )
        );

        this.points = parseStringPoints(omeroPolygon.points());
    }

    /**
     * Create a polygon from a {@link PathObject}.
     *
     * @param pathObject the {@link PathObject} to create the polygon from
     * @param roi the ROI containing the points of the polygon to create
     * @param fillColor whether this shape should have a fill color
     * @throws NullPointerException if the provided path object is null
     */
    public Polygon(PathObject pathObject, ROI roi, boolean fillColor) {
        super(pathObject, fillColor);

        this.points = roi.getAllPoints();
    }

    @Override
    public String createJson() {
        return gson.toJson(new OmeroPolygon(
                getId(),
                getOldId(),
                OmeroPolygon.TYPE,
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
        return ROIs.createPolygonROI(points, getPlane());
    }

    @Override
    public String toString() {
        return String.format("Polygon of ID %d and points %s", getId(), points);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Polygon polygon))
            return false;
        return polygon.points.equals(this.points);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), points);
    }
}
