package qupath.ext.omero.core.apis.commonentities.shapes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroPolygon;
import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;
import java.util.Optional;

public class TestPolygon {

    @Test
    void Check_Json_When_Created_From_Omero_Polygon() {
        String expectedJson = """
            {
                "@id": 83,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Polygon",
                "Text": "",
                "FillColor": 3,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "Points": "4.5,-7.5 12,65.5 0,50"
            }
            """;
        Polygon polygon = createFromOmeroPolygon();

        String json = polygon.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Omero_Polygon() {
        String expectedOldId = "53:83";
        Polygon polygon = createFromOmeroPolygon();

        String oldId = polygon.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_Full_Name_When_Created_From_Omero_Polygon() {
        String expectedOwnerFullName = "first middle last";
        Polygon polygon = createFromOmeroPolygon();

        String ownerFullName = polygon.getOwnerFullName().orElse(null);

        Assertions.assertEquals(expectedOwnerFullName, ownerFullName);
    }

    @Test
    void Check_Json_When_Created_From_Path_Object() {
        String expectedJson = """
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
        Polygon polygon = createFromPathObject();

        String json = polygon.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Path_Object() {
        String expectedOldId = "0:0";
        Polygon polygon = createFromPathObject();

        String oldId = polygon.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_Full_Name_When_Created_From_Path_Object() {
        Polygon polygon = createFromPathObject();

        Optional<String> ownerFullName = polygon.getOwnerFullName();

        Assertions.assertTrue(ownerFullName.isEmpty());
    }

    private static Polygon createFromOmeroPolygon() {
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

    private static Polygon createFromPathObject() {
        ROI roi = ROIs.createPolygonROI(
                List.of(
                        new Point2(4.5, -7.5),
                        new Point2(12, 65.5),
                        new Point2(0, 50)
                ),
                ImagePlane.getPlaneWithChannel(1, 2, 3)
        );

        return new Polygon(
                PathObjects.createAnnotationObject(roi, PathClass.fromString("some class", 5)),
                roi,
                false
        );
    }
}
