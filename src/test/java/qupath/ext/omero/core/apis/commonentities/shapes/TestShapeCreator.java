package qupath.ext.omero.core.apis.commonentities.shapes;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroEllipse;
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

    //TODO: other shapes from json

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
        List<? extends Shape> expectedShapes = List.of(createEllipse());

        List<? extends Shape> shapes = ShapeCreator.createShapes(pathObject, false);

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedShapes, shapes);
    }

    //TODO: other shapes from path object

    private static Ellipse createEllipse() {
        return new Ellipse(
                new OmeroEllipse(
                        83L,
                        OmeroEllipse.TYPE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
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
