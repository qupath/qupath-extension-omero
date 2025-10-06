package qupath.ext.omero.core.entities.shapes2;

import com.google.gson.Gson;
import qupath.ext.omero.core.entities.shapes2.omeroshapes.OmeroEllipse;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

public class Ellipse extends Shape {

    private static final Gson gson = new Gson();
    private final double x;
    private final double y;
    private final double radiusX;
    private final double radiusY;

    public Ellipse(OmeroEllipse omeroEllipse) {
        super(
                omeroEllipse.id(),
                omeroEllipse.text(),
                omeroEllipse.fillColor() == null ? null : rgbaToColor(omeroEllipse.fillColor()),
                omeroEllipse.strokeColor() == null ? null : rgbaToColor(omeroEllipse.strokeColor()),
                omeroEllipse.locked(),
                omeroEllipse.c(),
                omeroEllipse.z(),
                omeroEllipse.t()
        );

        this.x = omeroEllipse.x();
        this.y = omeroEllipse.y();
        this.radiusX = omeroEllipse.radiusX();
        this.radiusY = omeroEllipse.radiusY();
    }

    public Ellipse(EllipseROI ellipseRoi) {

    }

    @Override
    public String createJson() {
        return gson.toJson(new OmeroEllipse(
                getId(),
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
                radiusY
        ));
    }

    @Override
    protected ROI createRoi() {
        return ROIs.createEllipseROI(x-radiusX, y-radiusY, radiusX*2, radiusY*2, getPlane());
    }

    @Override
    public String toString() {
        return String.format("Ellipse centered at {x: %f, y: %f} of radius {x: %f, y: %f}", x, y, radiusX, radiusY);
    }
}
