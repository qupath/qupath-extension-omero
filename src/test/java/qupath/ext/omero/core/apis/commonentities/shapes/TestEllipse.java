package qupath.ext.omero.core.apis.commonentities.shapes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroEllipse;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.Optional;
import java.util.UUID;

public class TestEllipse {

    @Test
    void Check_Json_When_Created_From_Omero_Ellipse() {
        String expectedJson = """
            {
                "@id": 83,
                "oldId": "53:83",
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Ellipse",
                "Text": "Annotation:NoClass:4f0a3bd5-2954-4110-a37c-bab2a01e8e2c:NoParent:NoName",
                "FillColor": 3,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X": 4.5,
                "Y": -7.5,
                "RadiusX": 12.0,
                "RadiusY": 65.5
            }
            """.replace(" ", "").replace("\n", "");
        Ellipse ellipse = createFromOmeroEllipse();

        String json = ellipse.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Omero_Ellipse() {
        String expectedOldId = "53:83";
        Ellipse ellipse = createFromOmeroEllipse();

        String oldId = ellipse.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_When_Created_From_Omero_Ellipse() {
        SimpleEntity expectedOwner = new SimpleEntity(73, "first middle last");
        Ellipse ellipse = createFromOmeroEllipse();

        SimpleEntity owner = ellipse.getOwner().orElseThrow();

        Assertions.assertEquals(expectedOwner, owner);
    }

    @Test
    void Check_Json_When_Created_From_Path_Object() {
        String expectedJson = """
            {
                "@id": 0,
                "oldId": "0:0",
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Ellipse",
                "Text": "Annotation:someClass:886b8740-8a3b-4305-b73b-c06273746d3e:NoParent:NoName",
                "FillColor": 0,
                "StrokeColor": 255,
                "Locked": false,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X": 4.5,
                "Y": -7.5,
                "RadiusX": 12.0,
                "RadiusY": 65.5
            }
            """.replace(" ", "").replace("\n", "");
        Ellipse ellipse = createFromPathObject();

        String json = ellipse.createJson();

        Assertions.assertEquals(expectedJson, json);
    }

    @Test
    void Check_Old_Id_When_Created_From_Path_Object() {
        String expectedOldId = "0:0";
        Ellipse ellipse = createFromPathObject();

        String oldId = ellipse.getOldId();

        Assertions.assertEquals(expectedOldId, oldId);
    }

    @Test
    void Check_Owner_When_Created_From_Path_Object() {
        Ellipse ellipse = createFromPathObject();

        Optional<SimpleEntity> owner = ellipse.getOwner();

        Assertions.assertTrue(owner.isEmpty());
    }

    private static Ellipse createFromOmeroEllipse() {
        return new Ellipse(
                new OmeroEllipse(
                        83L,
                        "53:83",
                        OmeroEllipse.TYPE,
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

    private static Ellipse createFromPathObject() {
        PathObject pathObject = PathObjects.createAnnotationObject(
                ROIs.createEllipseROI(
                        4.5 - 12,         // top left, not center
                        -7.5 - 65.5,         // top left, not center
                        12d * 2,             // diameter, not radius
                        65.5 * 2,            // diameter, not radius
                        ImagePlane.getPlaneWithChannel(1, 2, 3)
                ),
                PathClass.fromString("someClass", 0)
        );
        pathObject.setID(UUID.fromString("886b8740-8a3b-4305-b73b-c06273746d3e"));

        return new Ellipse(pathObject, false);
    }
}
