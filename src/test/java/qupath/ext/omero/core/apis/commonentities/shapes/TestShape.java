package qupath.ext.omero.core.apis.commonentities.shapes;

import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;

public class TestShape {

    @Test
    void Check_Path_Object_From_Ellipse() {
        PathObject expectedPathObject = PathObjects.createAnnotationObject(
                ROIs.createEllipseROI(
                        4.5,
                        -7.5,
                        12d * 2,      // diameter, not radius
                        65.5 * 2,            // same
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("some class", 5)
        );
        List<PathObject> expectedPathObjects = List.of(expectedPathObject);
        Ellipse ellipse = new Ellipse(expectedPathObject, false);

        List<PathObject> pathObjects = Shape.createPathObjects(List.of(ellipse));

        // PathObject does not override equals, so only the ID and the ROI are checked
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getID).toList(),
                pathObjects.stream().map(PathObject::getID).toList()
        );
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList(),
                pathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList()
        );
    }

    @Test
    void Check_Path_Object_From_Label() {
        PathObject expectedPathObject = PathObjects.createAnnotationObject(
                ROIs.createPointsROI(
                        4.5,
                        -7.5,
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("some class", 5)
        );
        List<PathObject> expectedPathObjects = List.of(expectedPathObject);
        Label label = new Label(expectedPathObject, false);

        List<PathObject> pathObjects = Shape.createPathObjects(List.of(label));

        // PathObject does not override equals, so only the ID and the ROI are checked
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getID).toList(),
                pathObjects.stream().map(PathObject::getID).toList()
        );
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList(),
                pathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList()
        );
    }

    @Test
    void Check_Path_Object_From_Line() {
        PathObject expectedPathObject = PathObjects.createAnnotationObject(
                ROIs.createLineROI(
                        4.5,
                        -7.5,
                        12d,
                        65.5,
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("some class", 5)
        );
        List<PathObject> expectedPathObjects = List.of(expectedPathObject);
        Line line = new Line(expectedPathObject, false);

        List<PathObject> pathObjects = Shape.createPathObjects(List.of(line));

        // PathObject does not override equals, so only the ID and the ROI are checked
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getID).toList(),
                pathObjects.stream().map(PathObject::getID).toList()
        );
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList(),
                pathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList()
        );
    }

    @Test
    void Check_Path_Object_From_Point() {
        PathObject expectedPathObject = PathObjects.createAnnotationObject(
                ROIs.createPointsROI(
                        4.5,
                        -7.5,
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("some class", 5)
        );
        List<PathObject> expectedPathObjects = List.of(expectedPathObject);
        Point point = Point.create(expectedPathObject, false).getFirst();

        List<PathObject> pathObjects = Shape.createPathObjects(List.of(point));

        // PathObject does not override equals, so only the ID and the ROI are checked
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getID).toList(),
                pathObjects.stream().map(PathObject::getID).toList()
        );
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList(),
                pathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList()
        );
    }

    @Test
    void Check_Path_Object_From_Polygon() {
        PathObject expectedPathObject = PathObjects.createAnnotationObject(
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
        List<PathObject> expectedPathObjects = List.of(expectedPathObject);
        Polygon polygon = new Polygon(expectedPathObject, expectedPathObject.getROI(), false);

        List<PathObject> pathObjects = Shape.createPathObjects(List.of(polygon));

        // PathObject does not override equals, so only the ID and the ROI are checked
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getID).toList(),
                pathObjects.stream().map(PathObject::getID).toList()
        );
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList(),
                pathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList()
        );
    }

    @Test
    void Check_Path_Object_From_Polyline() {
        PathObject expectedPathObject = PathObjects.createAnnotationObject(
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
        List<PathObject> expectedPathObjects = List.of(expectedPathObject);
        Polyline polyline = new Polyline(expectedPathObject, false);

        List<PathObject> pathObjects = Shape.createPathObjects(List.of(polyline));

        // PathObject does not override equals, so only the ID and the ROI are checked
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getID).toList(),
                pathObjects.stream().map(PathObject::getID).toList()
        );
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList(),
                pathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList()
        );
    }

    @Test
    void Check_Path_Object_From_Rectangle() {
        PathObject expectedPathObject = PathObjects.createAnnotationObject(
                ROIs.createRectangleROI(
                        4.5,
                        -7.5,
                        12d,
                        65.5,
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("some class", 5)
        );
        List<PathObject> expectedPathObjects = List.of(expectedPathObject);
        Rectangle rectangle = new Rectangle(expectedPathObject, false);

        List<PathObject> pathObjects = Shape.createPathObjects(List.of(rectangle));

        // PathObject does not override equals, so only the ID and the ROI are checked
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getID).toList(),
                pathObjects.stream().map(PathObject::getID).toList()
        );
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList(),
                pathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList()
        );
    }

    @Test
    void Check_Path_Objects_With_Two_Different_Shapes_And_Different_Id() {
        PathObject rectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(2, 3, 56, 76));
        PathObject ellipse = PathObjects.createAnnotationObject(ROIs.createEllipseROI(1, 6, 89, 6));
        Shape rectangleShape = new Rectangle(rectangle, false);
        Shape ellipseShape = new Ellipse(ellipse, false);
        List<PathObject> expectedPathObjects = List.of(rectangle, ellipse);

        List<PathObject> pathObjects = Shape.createPathObjects(List.of(rectangleShape, ellipseShape));

        // PathObject does not override equals, so only the ID and the ROI are checked
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getID).toList(),
                pathObjects.stream().map(PathObject::getID).toList()
        );
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList(),
                pathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList()
        );
    }

    @Test
    void Check_Path_Objects_With_Parent() {
        PathObject rectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(2, 3, 56, 76));
        PathObject ellipse = PathObjects.createAnnotationObject(ROIs.createEllipseROI(1, 6, 89, 6));
        rectangle.addChildObject(ellipse);
        Shape rectangleShape = new Rectangle(rectangle, false);
        Shape ellipseShape = new Ellipse(ellipse, false);
        List<PathObject> expectedPathObjects = List.of(rectangle);      // ellipse is not here because it's a child of rectangle

        List<PathObject> pathObjects = Shape.createPathObjects(List.of(rectangleShape, ellipseShape));

        // PathObject does not override equals, so only the ID and the ROI are checked
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getID).toList(),
                pathObjects.stream().map(PathObject::getID).toList()
        );
        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList(),
                pathObjects.stream().map(PathObject::getROI).map(ROI::getAllPoints).toList()
        );
    }
}
