package qupath.ext.omero.core.apis.commonentities.shapes;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroEllipse;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroLabel;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroLine;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroPoint;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroPolygon;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroPolyline;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroRectangle;
import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.GeometryROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A class to create {@link Shape shapes}.
 */
public class ShapeCreator {

    private static final Gson gson = new Gson();

    private ShapeCreator() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Create a {@link Shape} from a JSON element.
     *
     * @param json the JSON element containing the shape
     * @param roiId in OMERO, a ROI contains one or more shapes. This parameter is the ID of the ROI containing the shape to return
     * @return a {@link Shape} corresponding to the provided JSON element
     * @throws IllegalArgumentException if the provided JSON object does not correspond to an expected shape
     * @throws com.google.gson.JsonSyntaxException if the provided JSON object has an unexpected format
     * @throws RuntimeException if the provided JSON object is null or lacks mandatory fields to create a shape
     */
    public static Shape createShape(JsonElement json, long roiId) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException(String.format("The provided JSON %s is not a JSON object", json));
        }
        JsonObject jsonObject = json.getAsJsonObject();

        if (!jsonObject.has("@type") || !jsonObject.get("@type").isJsonPrimitive()) {
            throw new IllegalArgumentException(String.format("'@type' attribute not found in the provided JSON %s", json));
        }
        String type = jsonObject.get("@type").getAsString();

        return switch (type) {
            case OmeroEllipse.TYPE -> new Ellipse(gson.fromJson(json, OmeroEllipse.class), roiId);
            case OmeroLabel.TYPE -> new Label(gson.fromJson(json, OmeroLabel.class), roiId);
            case OmeroLine.TYPE -> new Line(gson.fromJson(json, OmeroLine.class), roiId);
            case OmeroPoint.TYPE -> new Point(gson.fromJson(json, OmeroPoint.class), roiId);
            case OmeroPolygon.TYPE -> new Polygon(gson.fromJson(json, OmeroPolygon.class), roiId);
            case OmeroPolyline.TYPE -> new Polyline(gson.fromJson(json, OmeroPolyline.class), roiId);
            case OmeroRectangle.TYPE -> new Rectangle(gson.fromJson(json, OmeroRectangle.class), roiId);
            default -> throw new IllegalArgumentException(String.format("Unexpected type %s", type));
        };
    }

    /**
     * Create a list of {@link Shape shapes} from a {@link PathObject}.
     * <ul>
     *     <li>
     *         If the {@link ROI} of the path object is a {@link PointsROI}, then a list of {@link Point} is returned.
     *     </li>
     *     <li>
     *         If the {@link ROI} of the path object is a {@link GeometryROI}, then this ROI is {@link RoiTools#splitROI(ROI) split},
     *         and for each split ROI, a {@link Polygon} is created for the exterior ring and zero or more other {@link Polygon} are
     *         created for each interior ring.
     *     </li>
     *     <li>Else, a corresponding shape is created (e.g. {@link Ellipse} from an {@link EllipseROI}).</li>
     * </ul>
     *
     * @param pathObject the path object to create the shape from
     * @param fillColor whether the created shapes should have a fill color
     * @return a list of {@link Shape shapes} corresponding to the provided {@link PathObject}
     * @throws NullPointerException if the provided path object is null
     * @throws IllegalArgumentException if the provided path object contains an unexpected {@link ROI}
     */
    public static List<? extends Shape> createShapes(PathObject pathObject, boolean fillColor) {
        return switch (pathObject.getROI()) {
            case EllipseROI ignored -> List.of(new Ellipse(pathObject, fillColor));
            case LineROI ignored -> List.of(new Line(pathObject, fillColor));
            case PointsROI ignored -> Point.create(pathObject, fillColor);
            case PolygonROI polygonRoi -> List.of(new Polygon(pathObject, polygonRoi, fillColor));
            case PolylineROI ignored -> List.of(new Polyline(pathObject, fillColor));
            case RectangleROI ignored -> List.of(new Rectangle(pathObject, fillColor));
            case GeometryROI geometryRoi -> RoiTools.splitROI(geometryRoi).stream()
                    .map(roi -> {
                        if (roi.getGeometry() instanceof org.locationtech.jts.geom.Polygon polygon) {
                            return Stream.concat(
                                    Stream.of(ROIs.createPolygonROI(
                                            Arrays.stream(polygon.getExteriorRing().getCoordinates())
                                                    .map(coordinate -> new Point2(coordinate.x, coordinate.y))
                                                    .toList(),
                                            roi.getImagePlane()
                                    )),
                                    IntStream.range(0, polygon.getNumInteriorRing())
                                            .mapToObj(i -> ROIs.createPolygonROI(
                                                    Arrays.stream(polygon.getInteriorRingN(i).getCoordinates())
                                                            .map(coordinate -> new Point2(coordinate.x, coordinate.y))
                                                            .toList(),
                                                    roi.getImagePlane()
                                            ))
                            ).toList();
                        } else {
                            return List.of(roi);
                        }
                    })
                    .flatMap(List::stream)
                    .map(polygonRoi -> new Polygon(pathObject, polygonRoi, fillColor))
                    .toList();
            default -> throw new IllegalArgumentException(String.format("Unexpected ROI %s. Cannot convert it to a shape", pathObject.getROI()));
        };
    }
}
