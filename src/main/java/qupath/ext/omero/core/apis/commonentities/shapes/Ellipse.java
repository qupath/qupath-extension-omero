package qupath.ext.omero.core.apis.commonentities.shapes;

import com.google.gson.Gson;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroEllipse;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Objects;

/**
 * An ellipse.
 */
public class Ellipse extends Shape {

    private static final Gson gson = new Gson();
    private final double x;
    private final double y;
    private final double radiusX;
    private final double radiusY;

    /**
     * Create an ellipse from an {@link OmeroEllipse}.
     *
     * @param omeroEllipse the OMERO ellipse to create the ellipse from
     * @param roiId in OMERO, a ROI contains one or more shapes. This parameter is the ID of the ROI containing this shape
     * @throws NullPointerException if the provided OMERO ellipse is null
     */
    public Ellipse(OmeroEllipse omeroEllipse, long roiId) {
        super(
                omeroEllipse.id(),
                roiId,
                omeroEllipse.text(),
                omeroEllipse.fillColor() == null ? null : rgbaToColor(omeroEllipse.fillColor()),
                omeroEllipse.strokeColor() == null ? null : rgbaToColor(omeroEllipse.strokeColor()),
                omeroEllipse.locked() != null && omeroEllipse.locked(),
                omeroEllipse.c() == null ? 0 : omeroEllipse.c(),
                omeroEllipse.z() == null ? 0 : omeroEllipse.z(),
                omeroEllipse.t() == null ? 0 : omeroEllipse.t(),
                omeroEllipse.omeroDetails() == null || omeroEllipse.omeroDetails().experimenter() == null ?
                        null :
                        new SimpleEntity(
                                omeroEllipse.omeroDetails().experimenter().id(),
                                omeroEllipse.omeroDetails().experimenter().fullName()
                        )
        );

        this.x = omeroEllipse.x();
        this.y = omeroEllipse.y();
        this.radiusX = omeroEllipse.radiusX();
        this.radiusY = omeroEllipse.radiusY();
    }

    /**
     * Create an ellipse from a {@link PathObject}.
     * <p>
     * Note that when creating an ellipse this way, some OMERO-specific data won't be defined (e.g. null owner, IDs defined to 0, etc.).
     *
     * @param pathObject the {@link PathObject} to create the ellipse from
     * @param fillColor whether this shape should have a fill color
     * @throws NullPointerException if the provided path object is null or doesn't have a {@link ROI}
     */
    public Ellipse(PathObject pathObject, boolean fillColor) {
        super(pathObject, fillColor);

        this.x = pathObject.getROI().getCentroidX();
        this.y = pathObject.getROI().getCentroidY();
        this.radiusX = pathObject.getROI().getBoundsWidth()/2;
        this.radiusY = pathObject.getROI().getBoundsHeight()/2;
    }

    @Override
    public String createJson() {
        return gson.toJson(new OmeroEllipse(
                getId(),
                getOldId(),
                OmeroEllipse.TYPE,
                getText(),
                getFillColor().map(Shape::colorToRgba).orElse(null),
                getStrokeColor().map(Shape::colorToRgba).orElse(null),
                getLocked(),
                getPlane().getC(),
                getPlane().getZ(),
                getPlane().getT(),
                x,
                y,
                radiusX,
                radiusY,
                null
        ));
    }

    @Override
    public String toString() {
        return String.format("Ellipse of ID %d centered at {x: %f, y: %f} of radius {x: %f, y: %f}", getId(), x, y, radiusX, radiusY);
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
        return Objects.hash(getId(), x, y, radiusX, radiusY);
    }

    @Override
    protected ROI createRoi() {
        return ROIs.createEllipseROI(x-radiusX, y-radiusY, radiusX*2, radiusY*2, getPlane());
    }
}
