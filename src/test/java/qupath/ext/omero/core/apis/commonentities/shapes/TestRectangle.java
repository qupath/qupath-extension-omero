package qupath.ext.omero.core.apis.commonentities.shapes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroRectangle;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.Optional;

public class TestRectangle {

    @Test
    void Check_Json_When_Created_From_Omero_Rectangle() {
        String expectedJson = """
            {
                "@id": 83,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Rectangle",
                "Text": "",
                "FillColor": 3,
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
        Rectangle rectangle = createFromOmeroRectangle();

        String json = rectangle.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Omero_Rectangle() {
        String expectedOldId = "53:83";
        Rectangle rectangle = createFromOmeroRectangle();

        String oldId = rectangle.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_When_Created_From_Omero_Rectangle() {
        SimpleEntity expectedOwner = new SimpleEntity(73, "first middle last");
        Rectangle rectangle = createFromOmeroRectangle();

        SimpleEntity owner = rectangle.getOwner().orElse(null);

        Assertions.assertEquals(expectedOwner, owner);
    }

    @Test
    void Check_Json_When_Created_From_Path_Object() {
        String expectedJson = """
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
        Rectangle rectangle = createFromPathObject();

        String json = rectangle.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Path_Object() {
        String expectedOldId = "0:0";
        Rectangle rectangle = createFromPathObject();

        String oldId = rectangle.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_When_Created_From_Path_Object() {
        Rectangle rectangle = createFromPathObject();

        Optional<SimpleEntity> owner = rectangle.getOwner();

        Assertions.assertTrue(owner.isEmpty());
    }

    private static Rectangle createFromOmeroRectangle() {
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

    private static Rectangle createFromPathObject() {
        return new Rectangle(
                PathObjects.createAnnotationObject(
                        ROIs.createRectangleROI(
                                4.5,
                                -7.5,
                                12d,
                                65.5,
                                ImagePlane.getPlaneWithChannel(1, 2, 3)
                        ),
                        PathClass.fromString("some class", 5)
                ),
                false
        );
    }
}
