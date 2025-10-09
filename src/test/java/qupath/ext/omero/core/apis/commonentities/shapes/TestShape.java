package qupath.ext.omero.core.apis.commonentities.shapes;

import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
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

    //TODO: other shapes

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
