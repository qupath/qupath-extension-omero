package qupath.ext.omero.core.apis.commonentities.shapes;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroLabel;
import qupath.ext.omero.core.apis.json.permissions.Experimenter;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Objects;

/**
 * A text placed at some point.
 * This is not supported by the extension, which will creates a point instead.
 */
public class Label extends Shape {

    private static final Logger logger = LoggerFactory.getLogger(Label.class);
    private static final Gson gson = new Gson();
    private final double x;
    private final double y;

    /**
     * Create a label from an {@link OmeroLabel}.
     *
     * @param omeroLabel the OMERO label to create the label from
     * @param roiId in OMERO, a ROI contains one or more shapes. This parameter is the ID of the ROI containing this shape
     * @throws NullPointerException if the provided OMERO label is null
     */
    public Label(OmeroLabel omeroLabel, long roiId) {
        super(
                omeroLabel.id(),
                roiId,
                omeroLabel.text(),
                omeroLabel.fillColor() == null ? null : rgbaToColor(omeroLabel.fillColor()),
                omeroLabel.strokeColor() == null ? null : rgbaToColor(omeroLabel.strokeColor()),
                omeroLabel.locked(),
                omeroLabel.c(),
                omeroLabel.z(),
                omeroLabel.t(),
                omeroLabel.omeroDetails() == null || omeroLabel.omeroDetails().experimenter() == null ?
                        null :
                        new Experimenter(omeroLabel.omeroDetails().experimenter())
        );

        this.x = omeroLabel.x();
        this.y = omeroLabel.y();
    }

    /**
     * Create a label from a {@link PathObject}.
     *
     * @param pathObject the {@link PathObject} to create the label from
     * @param fillColor whether this shape should have a fill color
     * @throws NullPointerException if the provided path object is null or doesn't have a {@link ROI}
     */
    public Label(PathObject pathObject, boolean fillColor) {
        super(pathObject, fillColor);

        this.x = pathObject.getROI().getCentroidX();
        this.y = pathObject.getROI().getCentroidY();
    }

    @Override
    public String createJson() {
        return gson.toJson(new OmeroLabel(
                getId(),
                OmeroLabel.TYPE,
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
        logger.warn("Creating point (requested label shape is unsupported)");

        return ROIs.createPointsROI(x, y, getPlane());
    }

    @Override
    public String toString() {
        return String.format("Label located at {x: %f, y: %f}", x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Label label))
            return false;
        return label.getId() == this.getId() &&
                label.x == this.x && label.y == this.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), x, y);
    }
}
