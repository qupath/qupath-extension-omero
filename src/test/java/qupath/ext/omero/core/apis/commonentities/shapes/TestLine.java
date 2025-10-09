package qupath.ext.omero.core.apis.commonentities.shapes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroLine;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.Optional;

public class TestLine {

    @Test
    void Check_Json_When_Created_From_Omero_Line() {
        String expectedJson = """
            {
                "@id": 83,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Line",
                "Text": "",
                "FillColor": 3,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X1": 4.5,
                "Y1": -7.5,
                "X2": 12,
                "Y2": 65.5
            }
            """;
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
    void Check_Owner_Full_Name_When_Created_From_Omero_Line() {
        String expectedOwnerFullName = "first middle last";
        Line line = createFromOmeroLine();

        String ownerFullName = line.getOwnerFullName().orElse(null);

        Assertions.assertEquals(expectedOwnerFullName, ownerFullName);
    }

    @Test
    void Check_Json_When_Created_From_Path_Object() {
        String expectedJson = """
            {
                "@id": 0,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Line",
                "Text": "",
                "FillColor": 0,
                "StrokeColor": 5,
                "Locked": true,
                "TheC": 1,
                "TheZ": 2,
                "TheT": 3,
                "X1": 4.5,
                "Y1": -7.5,
                "X2": 12,
                "Y2": 65.5
            }
            """;
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
    void Check_Owner_Full_Name_When_Created_From_Path_Object() {
        Line line = createFromPathObject();

        Optional<String> ownerFullName = line.getOwnerFullName();

        Assertions.assertTrue(ownerFullName.isEmpty());
    }

    private static Line createFromOmeroLine() {
        return new Line(
                new OmeroLine(
                        83L,
                        OmeroLine.TYPE,
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
                                        "last",
                                        null,
                                        null,
                                        null
                                ),
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                ),
                53
        );
    }

    private static Line createFromPathObject() {
        return new Line(
                PathObjects.createAnnotationObject(
                        ROIs.createLineROI(
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
