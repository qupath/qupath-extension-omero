package qupath.ext.omero.core.apis.commonentities.shapes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.shapes.OmeroLabel;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.ROIs;

import java.util.Optional;

public class TestLabel {

    @Test
    void Check_Json_When_Created_From_Omero_Label() {
        String expectedJson = """
            {
                "@id": 83,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Label",
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
    void Check_Owner_Full_Name_When_Created_From_Omero_Label() {
        String expectedOwnerFullName = "first middle last";
        Label label = createFromOmeroLabel();

        String ownerFullName = label.getOwnerFullName().orElse(null);

        Assertions.assertEquals(expectedOwnerFullName, ownerFullName);
    }

    @Test
    void Check_Json_When_Created_From_Path_Object() {
        String expectedJson = """
            {
                "@id": 0,
                "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Label",
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
    void Check_Owner_Full_Name_When_Created_From_Path_Object() {
        Label label = createFromPathObject();

        Optional<String> ownerFullName = label.getOwnerFullName();

        Assertions.assertTrue(ownerFullName.isEmpty());
    }

    private static Label createFromOmeroLabel() {
        return new Label(
                new OmeroLabel(
                        83L,
                        OmeroLabel.TYPE,
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

    private static Label createFromPathObject() {
        return new Label(
                PathObjects.createAnnotationObject(
                        ROIs.createPointsROI(
                                4.5,
                                -7.5
                        ),
                        PathClass.fromString("some class", 5)
                ),
                false
        );
    }
}
