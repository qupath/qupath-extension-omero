package qupath.ext.omero.core.apis.commonentities.shapes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroPolyline;
import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TestPolyline {

    @Test
    void Check_Json_When_Created_From_Omero_Polyline() {
        String expectedJson = """
            {
                "@id": 83,
                "oldId": "53:83",
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Polyline",
                "Text": "Annotation:NoClass:4f0a3bd5-2954-4110-a37c-bab2a01e8e2c:NoParent:NoName",
                "FillColor": 3,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "Points": "4.5,-7.5$12.0,65.5$0.0,50.0"
            }
            """.replace(" ", "").replace("\n", "").replace("$", " ");
                                                            // "#" replace is done to preserve necessary white spaces in Points
        Polyline polyline = createFromOmeroPolyline();

        String json = polyline.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Omero_Polyline() {
        String expectedOldId = "53:83";
        Polyline polyline = createFromOmeroPolyline();

        String oldId = polyline.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_When_Created_From_Omero_Polyline() {
        SimpleEntity expectedOwner = new SimpleEntity(73, "first middle last");
        Polyline polyline = createFromOmeroPolyline();

        SimpleEntity owner = polyline.getOwner().orElseThrow();

        Assertions.assertEquals(expectedOwner, owner);
    }

    @Test
    void Check_Json_When_Created_From_Path_Object() {
        String expectedJson = """
            {
                "@id": 0,
                "oldId": "0:0",
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Polyline",
                "Text": "Annotation:someClass:886b8740-8a3b-4305-b73b-c06273746d3e:NoParent:NoName",
                "FillColor": 0,
                "StrokeColor": 255,
                "Locked": false,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "Points": "4.5,-7.5$12.0,65.5$0.0,50.0"
            }
            """.replace(" ", "").replace("\n", "").replace("$", " ");
                                                        // "#" replace is done to preserve necessary white spaces in Points
        Polyline polyline = createFromPathObject();

        String json = polyline.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Path_Object() {
        String expectedOldId = "0:0";
        Polyline polyline = createFromPathObject();

        String oldId = polyline.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_When_Created_From_Path_Object() {
        Polyline polyline = createFromPathObject();

        Optional<SimpleEntity> owner = polyline.getOwner();

        Assertions.assertTrue(owner.isEmpty());
    }

    private static Polyline createFromOmeroPolyline() {
        return new Polyline(
                new OmeroPolyline(
                        83L,
                        "53:83",
                        OmeroPolyline.TYPE,
                        "Annotation:NoClass:4f0a3bd5-2954-4110-a37c-bab2a01e8e2c:NoParent:NoName",
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

    private static Polyline createFromPathObject() {
        PathObject pathObject = PathObjects.createAnnotationObject(
                ROIs.createPolylineROI(
                        List.of(
                                new Point2(4.5, -7.5),
                                new Point2(12, 65.5),
                                new Point2(0, 50)
                        ),
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("someClass", 0)
        );
        pathObject.setID(UUID.fromString("886b8740-8a3b-4305-b73b-c06273746d3e"));

        return new Polyline(pathObject, false);
    }
}
