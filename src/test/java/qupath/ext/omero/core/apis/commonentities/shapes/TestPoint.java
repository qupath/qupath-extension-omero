package qupath.ext.omero.core.apis.commonentities.shapes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroPoint;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.Optional;

public class TestPoint {

    @Test
    void Check_Json_When_Created_From_Omero_Point() {
        String expectedJson = """
            {
                "@id": 83,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Point",
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
        Point point = createFromOmeroPoint();

        String json = point.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Omero_Point() {
        String expectedOldId = "53:83";
        Point point = createFromOmeroPoint();

        String oldId = point.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_Full_Name_When_Created_From_Omero_Point() {
        String expectedOwnerFullName = "first middle last";
        Point point = createFromOmeroPoint();

        String ownerFullName = point.getOwnerFullName().orElse(null);

        Assertions.assertEquals(expectedOwnerFullName, ownerFullName);
    }

    @Test
    void Check_Json_When_Created_From_Path_Object() {
        String expectedJson = """
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
        Point point = createFromPathObject();

        String json = point.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Path_Object() {
        String expectedOldId = "0:0";
        Point point = createFromPathObject();

        String oldId = point.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_Full_Name_When_Created_From_Path_Object() {
        Point point = createFromPathObject();

        Optional<String> ownerFullName = point.getOwnerFullName();

        Assertions.assertTrue(ownerFullName.isEmpty());
    }

    private static Point createFromOmeroPoint() {
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
                        new OmeroDetails(
                                new OmeroExperimenter(
                                        OmeroExperimenter.TYPE,
                                        73L,
                                        "first",
                                        "middle",
                                        "last"
                                ),
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                ),
                53
        );
    }

    private static Point createFromPathObject() {
        return Point.create(
                PathObjects.createAnnotationObject(
                        ROIs.createPointsROI(
                                4.5,
                                -7.5,
                                ImagePlane.getPlaneWithChannel(1, 2, 3)
                        ),
                        PathClass.fromString("some class", 5)
                ),
                false
        ).getFirst();
    }
}
