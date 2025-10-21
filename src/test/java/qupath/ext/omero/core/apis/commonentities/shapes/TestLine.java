package qupath.ext.omero.core.apis.commonentities.shapes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroLine;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.Optional;
import java.util.UUID;

public class TestLine {

    @Test
    void Check_Json_When_Created_From_Omero_Line() {
        String expectedJson = """
            {
                "@id": 83,
                "oldId": "53:83",
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Line",
                "Text": "Annotation:NoClass:4f0a3bd5-2954-4110-a37c-bab2a01e8e2c:NoParent:NoName",
                "FillColor": 3,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X1": 4.5,
                "Y1": -7.5,
                "X2": 12.0,
                "Y2": 65.5
            }
            """.replace(" ", "").replace("\n", "");
        Line line = createFromOmeroLine();

        String json = line.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Omero_Line() {
        String expectedOldId = "53:83";
        Line line = createFromOmeroLine();

        String oldId = line.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_When_Created_From_Omero_Line() {
        SimpleEntity expectedOwner = new SimpleEntity(73, "first middle last");
        Line line = createFromOmeroLine();

        SimpleEntity owner = line.getOwner().orElseThrow();

        Assertions.assertEquals(expectedOwner, owner);
    }

    @Test
    void Check_Json_When_Created_From_Path_Object() {
        String expectedJson = """
            {
                "@id": 0,
                "oldId": "0:0",
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Line",
                "Text": "Annotation:someClass:886b8740-8a3b-4305-b73b-c06273746d3e:NoParent:NoName",
                "FillColor": 0,
                "StrokeColor": 255,
                "Locked": false,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X1": 4.5,
                "Y1": -7.5,
                "X2": 12.0,
                "Y2": 65.5
            }
            """.replace(" ", "").replace("\n", "");
        Line line = createFromPathObject();

        String json = line.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Path_Object() {
        String expectedOldId = "0:0";
        Line line = createFromPathObject();

        String oldId = line.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_When_Created_From_Path_Object() {
        Line line = createFromPathObject();

        Optional<SimpleEntity> owner = line.getOwner();

        Assertions.assertTrue(owner.isEmpty());
    }

    private static Line createFromOmeroLine() {
        return new Line(
                new OmeroLine(
                        83L,
                        "53:83",
                        OmeroLine.TYPE,
                        "Annotation:NoClass:4f0a3bd5-2954-4110-a37c-bab2a01e8e2c:NoParent:NoName",
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

    private static Line createFromPathObject() {
        PathObject pathObject = PathObjects.createAnnotationObject(
                ROIs.createLineROI(
                        4.5,
                        -7.5,
                        12d,
                        65.5,
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("someClass", 0)
        );
        pathObject.setID(UUID.fromString("886b8740-8a3b-4305-b73b-c06273746d3e"));

        return new Line(pathObject, false);
    }
}
