package qupath.ext.omero.core.entities.shapes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.commonentities.shapes.Label;
import qupath.ext.omero.core.apis.commonentities.shapes.Shape;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.interfaces.ROI;

public class TestLabel {

    @Test
    void Check_Label_Created_From_JSON() {
        Shape label = createLabelFromJSON();

        Class<? extends Shape> type = label.getClass();

        Assertions.assertEquals(Label.class, type);
    }

    @Test
    void Check_ROI() {
        Shape label = createLabelFromJSON();

        Class<? extends ROI> roiClass = label.createRoi().getClass();

        Assertions.assertEquals(PointsROI.class, roiClass); // Labels are unsupported and converted to points
    }

    private Shape createLabelFromJSON() {
        return TestShape.createShapeFromJSON("""
                {
                    "@id": 713,
                    "text": "Annotation:Stroma:aba712b2-bbc2-4c05-bbba-d9fbab4d454f:NoParent",
                    "StrokeColor": -16776961,
                    "Locked": false,
                    "oldId": "454:713",
                    "@type": "http://www.openmicroscopy.org/Schemas/OME/2016-06#Label"
                }
                """);    // -16776961 is the integer representation of the red color in the BGR format
    }
}
