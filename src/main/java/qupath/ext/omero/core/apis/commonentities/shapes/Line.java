package qupath.ext.omero.core.apis.commonentities.shapes;

import com.google.gson.Gson;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroLine;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Objects;

/**
 * A line.
 */
public class Line extends Shape {

    private static final Gson gson = new Gson();
    private final double x1;
    private final double y1;
    private final double x2;
    private final double y2;

    /**
     * Create a line from an {@link OmeroLine}.
     *
     * @param omeroLine the OMERO line to create the line from
     * @param roiId in OMERO, a ROI contains one or more shapes. This parameter is the ID of the ROI containing this shape
     * @throws NullPointerException if the provided OMERO line is null
     */
    public Line(OmeroLine omeroLine, long roiId) {
        super(
                omeroLine.id(),
                roiId,
                omeroLine.text(),
                omeroLine.fillColor() == null ? null : rgbaToColor(omeroLine.fillColor()),
                omeroLine.strokeColor() == null ? null : rgbaToColor(omeroLine.strokeColor()),
                omeroLine.locked(),
                omeroLine.c(),
                omeroLine.z(),
                omeroLine.t(),
                omeroLine.omeroDetails() == null || omeroLine.omeroDetails().experimenter() == null ?
                        null :
                        new SimpleEntity(
                                omeroLine.omeroDetails().experimenter().id(),
                                omeroLine.omeroDetails().experimenter().fullName()
                        )
        );

        this.x1 = omeroLine.x1();
        this.y1 = omeroLine.y1();
        this.x2 = omeroLine.x2();
        this.y2 = omeroLine.y2();
    }

    /**
     * Create a line from a {@link PathObject}.
     *
     * @param pathObject the {@link PathObject} to create the line from
     * @param fillColor whether this shape should have a fill color
     * @throws NullPointerException if the provided path object is null or doesn't have a {@link ROI}
     * @throws IllegalArgumentException if the provided path object doesn't have a {@link LineROI}
     */
    public Line(PathObject pathObject, boolean fillColor) {
        super(pathObject, fillColor);

        if (!(pathObject.getROI() instanceof LineROI lineRoi)) {
            throw new IllegalArgumentException(String.format("The provided path object %s doesn't have a line ROI. Can't create line", pathObject));
        }
        this.x1 = lineRoi.getX1();
        this.y1 = lineRoi.getY1();
        this.x2 = lineRoi.getX2();
        this.y2 = lineRoi.getY2();
    }

    @Override
    public String createJson() {
        return gson.toJson(new OmeroLine(
                getId(),
                getOldId(),
                OmeroLine.TYPE,
                getText(),
                getFillColor().map(Shape::colorToRgba).orElse(null),
                getStrokeColor().map(Shape::colorToRgba).orElse(null),
                getLocked(),
                getPlane().getC(),
                getPlane().getZ(),
                getPlane().getT(),
                x1,
                y1,
                x2,
                y2,
                null
        ));
    }

    @Override
    protected ROI createRoi() {
        return ROIs.createLineROI(x1, y1, x2, y2, getPlane());
    }

    @Override
    public String toString() {
        return String.format("Line of ID %d located between {x1: %f, y1: %f} and {x2: %f, y2: %f}", getId(), x1, y1, x2, y2);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Line line))
            return false;
        return line.x1 == this.x1 && line.y1 == this.y1 && line.x2 == this.x2 && line.y2 == this.y2;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), x1, y1, x2, y2);
    }
}
