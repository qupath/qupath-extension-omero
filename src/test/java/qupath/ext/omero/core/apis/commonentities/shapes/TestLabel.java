package qupath.ext.omero.core.apis.commonentities.shapes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroLabel;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.Optional;
import java.util.UUID;

public class TestLabel {

    @Test
    void Check_Json_When_Created_From_Omero_Label() {
        String expectedJson = """
            {
                "@id": 83,
                "oldId": "53:83",
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Label",
                "Text": "Annotation:NoClass:4f0a3bd5-2954-4110-a37c-bab2a01e8e2c:NoParent:NoName",
                "FillColor": 3,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X": 4.5,
                "Y": -7.5
            }
            """.replace(" ", "").replace("\n", "");
        Label label = createFromOmeroLabel();

        String json = label.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Omero_Label() {
        String expectedOldId = "53:83";
        Label label = createFromOmeroLabel();

        String oldId = label.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_When_Created_From_Omero_Label() {
        SimpleEntity expectedOwner = new SimpleEntity(73, "first middle last");
        Label label = createFromOmeroLabel();

        SimpleEntity owner = label.getOwner().orElseThrow();

        Assertions.assertEquals(expectedOwner, owner);
    }

    @Test
    void Check_Json_When_Created_From_Path_Object() {
        String expectedJson = """
            {
                "@id": 0,
                "oldId": "0:0",
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Label",
                "Text": "Annotation:someClass:323448d8-d622-4ebf-9512-824529af6083:NoParent:NoName",
                "FillColor": 0,
                "StrokeColor": 255,
                "Locked": false,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X": 4.5,
                "Y": -7.5
            }
            """.replace(" ", "").replace("\n", "");
        Label label = createFromPathObject();

        String json = label.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Path_Object() {
        String expectedOldId = "0:0";
        Label label = createFromPathObject();

        String oldId = label.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_When_Created_From_Path_Object() {
        Label label = createFromPathObject();

        Optional<SimpleEntity> owner = label.getOwner();

        Assertions.assertTrue(owner.isEmpty());
    }

    private static Label createFromOmeroLabel() {
        return new Label(
                new OmeroLabel(
                        83L,
                        "53:83",
                        OmeroLabel.TYPE,
                        "Annotation:NoClass:4f0a3bd5-2954-4110-a37c-bab2a01e8e2c:NoParent:NoName",
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

    private static Label createFromPathObject() {
        PathObject pathObject = PathObjects.createAnnotationObject(
                ROIs.createPointsROI(
                        4.5,
                        -7.5,
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("someClass", 0)
        );
        pathObject.setID(UUID.fromString("323448d8-d622-4ebf-9512-824529af6083"));

        return new Label(pathObject, false);
    }
}
