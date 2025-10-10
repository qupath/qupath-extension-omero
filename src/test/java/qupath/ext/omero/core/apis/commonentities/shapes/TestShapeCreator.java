package qupath.ext.omero.core.apis.commonentities.shapes;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroEllipse;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroLabel;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroLine;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroPoint;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroPolygon;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroPolyline;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroRectangle;
import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.List;

public class TestShapeCreator {

    @Test
    void Check_Ellipse_From_Json() {
        Shape expectedShape = createEllipse();
        String json = """
            {
                "@id": 34,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Ellipse",
                "Text": "",
                "FillColor": 4,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X": 4.5,
                "Y": -7.5,
                "RadiusX": 12,
                "TheC": 65.5
            }
            """;

        Shape shape = ShapeCreator.createShape(new Gson().fromJson(json, JsonElement.class), 53);

        Assertions.assertEquals(expectedShape, shape);
    }

    @Test
    void Check_Label_From_Json() {
        Shape expectedShape = createLabel();
        String json = """
            {
                "@id": 83,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Label",
                "Text": "",
                "FillColor": 3,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X": 4.5,
                "Y": -7.5
            }
            """;

        Shape shape = ShapeCreator.createShape(new Gson().fromJson(json, JsonElement.class), 53);

        Assertions.assertEquals(expectedShape, shape);
    }

    @Test
    void Check_Line_From_Json() {
        Shape expectedShape = createLine();
        String json = """
            {
                "@id": 0,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Line",
                "Text": "",
                "FillColor": 0,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X1": 4.5,
                "Y1": -7.5,
                "X2": 12,
                "Y2": 65.5
            }
            """;

        Shape shape = ShapeCreator.createShape(new Gson().fromJson(json, JsonElement.class), 53);

        Assertions.assertEquals(expectedShape, shape);
    }

    @Test
    void Check_Point_From_Json() {
        Shape expectedShape = createPoint();
        String json = """
            {
                "@id": 0,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Point",
                "Text": "",
                "FillColor": 0,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X": 4.5,
                "Y": -7.5
            }
            """;

        Shape shape = ShapeCreator.createShape(new Gson().fromJson(json, JsonElement.class), 53);

        Assertions.assertEquals(expectedShape, shape);
    }

    @Test
    void Check_Polygon_From_Json() {
        Shape expectedShape = createPolygon();
        String json = """
            {
                "@id": 0,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Polygon",
                "Text": "",
                "FillColor": 0,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "Points": "4.5,-7.5 12,65.5 0,50"
            }
            """;

        Shape shape = ShapeCreator.createShape(new Gson().fromJson(json, JsonElement.class), 53);

        Assertions.assertEquals(expectedShape, shape);
    }

    @Test
    void Check_Polyline_From_Json() {
        Shape expectedShape = createPolyline();
        String json = """
            {
                "@id": 0,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Polyline",
                "Text": "",
                "FillColor": 0,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "Points": "4.5,-7.5 12,65.5 0,50"
            }
            """;

        Shape shape = ShapeCreator.createShape(new Gson().fromJson(json, JsonElement.class), 53);

        Assertions.assertEquals(expectedShape, shape);
    }

    @Test
    void Check_Rectangle_From_Json() {
        Shape expectedShape = createRectangle();
        String json = """
            {
                "@id": 0,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Rectangle",
                "Text": "",
                "FillColor": 0,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X": 4.5,
                "Y": -7.5,
                "Width": 12,
                "Height": 65.5
            }
            """;

        Shape shape = ShapeCreator.createShape(new Gson().fromJson(json, JsonElement.class), 53);

        Assertions.assertEquals(expectedShape, shape);
    }

    @Test
    void Check_Ellipse_From_Path_Object() {
        PathObject pathObject = PathObjects.createAnnotationObject(
                ROIs.createEllipseROI(
                        4.5,
                        -7.5,
                        12d * 2,      // diameter, not radius
                        65.5 * 2,            // same
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("some class", 5)
        );
        List<Shape> expectedShapes = List.of(createEllipse());

        List<Shape> shapes = ShapeCreator.createShapes(pathObject, false).stream().map(Shape.class::cast).toList();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedShapes, shapes);
    }

    @Test
    void Check_Label_From_Path_Object() {
        PathObject pathObject = PathObjects.createAnnotationObject(
                ROIs.createPointsROI(
                        4.5,
                        -7.5,
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("some class", 5)
        );
        List<Shape> expectedShapes = List.of(createLabel());

        List<Shape> shapes = ShapeCreator.createShapes(pathObject, false).stream().map(Shape.class::cast).toList();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedShapes, shapes);
    }

    @Test
    void Check_Line_From_Path_Object() {
        PathObject pathObject = PathObjects.createAnnotationObject(
                ROIs.createLineROI(
                        4.5,
                        -7.5,
                        12d,
                        65.5,
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("some class", 5)
        );
        List<Shape> expectedShapes = List.of(createLine());

        List<Shape> shapes = ShapeCreator.createShapes(pathObject, false).stream().map(Shape.class::cast).toList();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedShapes, shapes);
    }

    @Test
    void Check_Point_From_Path_Object() {
        PathObject pathObject = PathObjects.createAnnotationObject(
                ROIs.createPointsROI(
                        4.5,
                        -7.5,
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("some class", 5)
        );
        List<Shape> expectedShapes = List.of(createPoint());

        List<Shape> shapes = ShapeCreator.createShapes(pathObject, false).stream().map(Shape.class::cast).toList();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedShapes, shapes);
    }

    @Test
    void Check_Polygon_From_Path_Object() {
        PathObject pathObject = PathObjects.createAnnotationObject(
                ROIs.createPolygonROI(
                        List.of(
                                new Point2(4.5, -7.5),
                                new Point2(12, 65.5),
                                new Point2(0, 50)
                        ),
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("some class", 5)
        );
        List<Shape> expectedShapes = List.of(createPolygon());

        List<Shape> shapes = ShapeCreator.createShapes(pathObject, false).stream().map(Shape.class::cast).toList();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedShapes, shapes);
    }

    @Test
    void Check_Polyline_From_Path_Object() {
        PathObject pathObject = PathObjects.createAnnotationObject(
                ROIs.createPolylineROI(
                        List.of(
                                new Point2(4.5, -7.5),
                                new Point2(12, 65.5),
                                new Point2(0, 50)
                        ),
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("some class", 5)
        );
        List<Shape> expectedShapes = List.of(createPolyline());

        List<Shape> shapes = ShapeCreator.createShapes(pathObject, false).stream().map(Shape.class::cast).toList();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedShapes, shapes);
    }

    @Test
    void Check_Rectangle_From_Path_Object() {
        PathObject pathObject = PathObjects.createAnnotationObject(
                ROIs.createRectangleROI(
                        4.5,
                        -7.5,
                        12d,
                        65.5,
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("some class", 5)
        );
        List<Shape> expectedShapes = List.of(createRectangle());

        List<Shape> shapes = ShapeCreator.createShapes(pathObject, false).stream().map(Shape.class::cast).toList();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedShapes, shapes);
    }

    private static Ellipse createEllipse() {
        return new Ellipse(
                new OmeroEllipse(
                        83L,
                        OmeroEllipse.TYPE,
                        "",
                        3,
                        5,
                        true,
                        1,
                        2,
                        3,
                        4.5,
                        -7.5,
                        12d,
                        65.5,
                        null
                ),
                53
        );
    }

    private static Label createLabel() {
        return new Label(
                new OmeroLabel(
                        83L,
                        OmeroLabel.TYPE,
                        "",
                        3,
                        5,
                        true,
                        1,
                        2,
                        3,
                        4.5,
                        -7.5,
                        null
                ),
                53
        );
    }

    private static Line createLine() {
        return new Line(
                new OmeroLine(
                        83L,
                        OmeroLine.TYPE,
                        "",
                        3,
                        5,
                        true,
                        1,
                        2,
                        3,
                        4.5,
                        -7.5,
                        12d,
                        65.5,
                        null
                ),
                53
        );
    }

    private static Point createPoint() {
        return new Point(
                new OmeroPoint(
                        83L,
                        OmeroPoint.TYPE,
                        "",
                        3,
                        5,
                        true,
                        1,
                        2,
                        3,
                        4.5,
                        -7.5,
                        null
                ),
                53
        );
    }

    private static Polygon createPolygon() {
        return new Polygon(
                new OmeroPolygon(
                        83L,
                        OmeroPolygon.TYPE,
                        "",
                        3,
                        5,
                        true,
                        1,
                        2,
                        3,
                        "4.5,-7.5 12,65.5 0,50",
                        null
                ),
                53
        );
    }

    private static Polyline createPolyline() {
        return new Polyline(
                new OmeroPolyline(
                        83L,
                        OmeroPolyline.TYPE,
                        "",
                        3,
                        5,
                        true,
                        1,
                        2,
                        3,
                        "4.5,-7.5 12,65.5 0,50",
                        null
                ),
                53
        );
    }

    private static Rectangle createRectangle() {
        return new Rectangle(
                new OmeroRectangle(
                        83L,
                        OmeroRectangle.TYPE,
                        "",
                        3,
                        5,
                        true,
                        1,
                        2,
                        3,
                        4.5,
                        -7.5,
                        12d,
                        65.5,
                        null
                ),
                53
        );
    }
}
