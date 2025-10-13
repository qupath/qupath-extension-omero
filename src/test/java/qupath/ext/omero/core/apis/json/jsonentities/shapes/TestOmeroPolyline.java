package qupath.ext.omero.core.apis.json.jsonentities.shapes;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;

public class TestOmeroPolyline {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPolyline(
                        null,
                        "",
                        "",
                        1,
                        2,
                        true,
                        3,
                        4,
                        5,
                        "4.5,-7.5 12,65.5 0,50",
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                )
        );
    }

    @Test
    void Check_Points_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPolyline(
                        64L,
                        "",
                        "",
                        1,
                        2,
                        true,
                        3,
                        4,
                        5,
                        null,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroPolyline expectedOmeroPolyline = new OmeroPolyline(
                64L,
                "some type",
                "some text",
                1,
                2,
                true,
                3,
                4,
                5,
                "4.5,-7.5 12,65.5 0,50",
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroPolyline omeroPolyline = new Gson().fromJson(
                """
                {
                    "@id": 64,
                    "@type": "some type",
                    "Text": "some text",
                    "FillColor": 1,
                    "StrokeColor": 2,
                    "Locked": true,
                    "TheC": 3,
                    "TheZ": 4,
                    "TheT": 5,
                    "Points": "4.5,-7.5 12,65.5 0,50",
                    "omero:details:": {
                        "permissions": {
                            "isGroupWrite": false,
                            "isGroupRead": true,
                            "isGroupAnnotate": true
                        }
                    }
                }
                """,
                OmeroPolyline.class
        );

        Assertions.assertEquals(expectedOmeroPolyline, omeroPolyline);
    }
}
