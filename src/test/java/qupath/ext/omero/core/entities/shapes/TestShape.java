package qupath.ext.omero.core.entities.shapes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;

public class TestShape {

    public static Shape createShapeFromJSON(String json) {
        Gson gson = new GsonBuilder().registerTypeAdapter(Shape.class, new Shape.GsonShapeDeserializer()).create();
        JsonElement jsonElement = JsonParser.parseString(json);
        if (jsonElement.isJsonArray()) {
            return gson.fromJson(JsonParser.parseString(json).getAsJsonArray().get(0), Shape.class);
        }
        if (jsonElement.isJsonObject()) {
            return gson.fromJson(JsonParser.parseString(json).getAsJsonObject(), Shape.class);
        }
        throw new IllegalArgumentException("Invalid JSON: " + json);
    }

    @Test
    void Check_Path_Objects_With_Two_Different_Shapes_And_Different_Id() {
        PathObject rectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(2, 3, 56, 76, ImagePlane.getDefaultPlane()));
        PathObject ellipse = PathObjects.createAnnotationObject(ROIs.createEllipseROI(1, 6, 89, 6, ImagePlane.getDefaultPlane()));
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
    void Check_Path_Objects_With_Two_Different_Shapes_And_Same_Id() {
        PathObject rectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(2, 3, 56, 76, ImagePlane.getDefaultPlane()));
        PathObject ellipse = PathObjects.createAnnotationObject(ROIs.createEllipseROI(1, 6, 89, 6, ImagePlane.getDefaultPlane()));
        ellipse.setID(rectangle.getID());
        Shape rectangleShape = new Rectangle(rectangle, false);
        Shape ellipseShape = new Ellipse(ellipse, false);

        List<PathObject> pathObjects = Shape.createPathObjects(List.of(rectangleShape, ellipseShape));

        Assertions.assertEquals(1, pathObjects.size());
    }

    @Test
    void Check_Path_Objects_With_Two_Points() {
        PathObject point = PathObjects.createAnnotationObject(ROIs.createPointsROI(
                List.of(new Point2(2, 3), new Point2(6, 8)),
                ImagePlane.getDefaultPlane())
        );
        List<PathObject> expectedPathObjects = List.of(point);
        List<Point> shapes = Point.create(point, false);

        List<PathObject> pathObjects = Shape.createPathObjects(shapes);

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
        PathObject rectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(2, 3, 56, 76, ImagePlane.getDefaultPlane()));
        PathObject ellipse = PathObjects.createAnnotationObject(ROIs.createEllipseROI(1, 6, 89, 6, ImagePlane.getDefaultPlane()));
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

    @Test
    void Check_Old_ID() {
        Shape shape = createShape();

        String id = shape.getOldId();

        Assertions.assertEquals("454:713", id);
    }

    @Test
    void Check_Old_ID_After_Changed() {
        Shape shape = createShape();
        shape.setOldId(999);

        String id = shape.getOldId();

        Assertions.assertEquals("999:713", id);
    }

    @Test
    void Check_Owner_Full_Name() {
        String expectedOwnerFullName = "first middle last";
        Shape shape = createShape();

        String ownerFullName = shape.getOwnerFullName().orElse("");

        Assertions.assertEquals(expectedOwnerFullName, ownerFullName);
    }

    private Shape createShape() {
        String json = """
                {
                    "@id": 713,
                    "text": "Annotation:Stroma:aba712b2-bbc2-4c05-bbba-d9fbab4d454f:dfa7dfb2-fd32-4c05-bbba-d9fbab4d454f",
                    "StrokeColor": -16776961,
                    "Locked": false,
                    "oldId": "454:713",
                    "omero:details:": {
                        "owner": {
                            "FirstName": "first",
                            "MiddleName": "middle",
                            "LastName": "last"
                        }
                    }
                }
                """;    // -16776961 is the integer representation of the red color in the BGR format
        return new Gson().fromJson(json, ShapeImplementation.class);
    }

    private static class ShapeImplementation extends Shape {

        protected ShapeImplementation(String type) {
            super(type);
        }

        @Override
        protected ROI createRoi() {
            return ROIs.createEmptyROI();
        }
    }
}

