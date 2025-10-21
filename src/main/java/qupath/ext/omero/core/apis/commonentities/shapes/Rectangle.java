package qupath.ext.omero.core.apis.commonentities.shapes;

import com.google.gson.Gson;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroRectangle;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Objects;

/**
 * A rectangle.
 */
public class Rectangle extends Shape {

    private static final Gson gson = new Gson();
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    /**
     * Create a rectangle from an {@link OmeroRectangle}.
     *
     * @param omeroRectangle the OMERO rectangle to create the rectangle from
     * @param roiId in OMERO, a ROI contains one or more shapes. This parameter is the ID of the ROI containing this shape
     * @throws NullPointerException if the provided OMERO rectangle is null
     */
    public Rectangle(OmeroRectangle omeroRectangle, long roiId) {
        super(
                omeroRectangle.id(),
                roiId,
                omeroRectangle.text(),
                omeroRectangle.fillColor() == null ? null : rgbaToColor(omeroRectangle.fillColor()),
                omeroRectangle.strokeColor() == null ? null : rgbaToColor(omeroRectangle.strokeColor()),
                omeroRectangle.locked(),
                omeroRectangle.c(),
                omeroRectangle.z(),
                omeroRectangle.t(),
                omeroRectangle.omeroDetails() == null || omeroRectangle.omeroDetails().experimenter() == null ?
                        null :
                        new SimpleEntity(
                                omeroRectangle.omeroDetails().experimenter().id(),
                                omeroRectangle.omeroDetails().experimenter().fullName()
                        )
        );

        this.x = omeroRectangle.x();
        this.y = omeroRectangle.y();
        this.width = omeroRectangle.width();
        this.height = omeroRectangle.height();
    }

    /**
     * Create a rectangle from a {@link PathObject}.
     *
     * @param pathObject the {@link PathObject} to create the rectangle from
     * @param fillColor whether this shape should have a fill color
     * @throws NullPointerException if the provided path object is null or doesn't have a {@link ROI}
     */
    public Rectangle(PathObject pathObject, boolean fillColor) {
        super(pathObject, fillColor);

        this.x = pathObject.getROI().getBoundsX();
        this.y = pathObject.getROI().getBoundsY();
        this.width = pathObject.getROI().getBoundsWidth();
        this.height = pathObject.getROI().getBoundsHeight();
    }

    @Override
    public String createJson() {
        return gson.toJson(new OmeroRectangle(
                getId(),
                getOldId(),
                OmeroRectangle.TYPE,
                getText(),
                getFillColor().map(Shape::colorToRgba).orElse(null),
                getStrokeColor().map(Shape::colorToRgba).orElse(null),
                getLocked(),
                getPlane().getC(),
                getPlane().getZ(),
                getPlane().getT(),
                x,
                y,
                width,
                height,
                null
        ));
    }

    @Override
    protected ROI createRoi() {
        return ROIs.createRectangleROI(x, y, width, height, getPlane());
    }

    @Override
    public String toString() {
        return String.format("Rectangle of ID %d located (top left) at {x: %f, y: %f} of width %f and height %f", getId(), x, y, width, height);
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
        return Objects.hash(getId(), x, y, width, height);
    }
}
