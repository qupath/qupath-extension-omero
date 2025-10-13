package qupath.ext.omero.core.apis.json.jsonentities.shapes;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;

public class TestOmeroLabel {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroLabel(
                        null,
                        "",
                        "",
                        1,
                        2,
                        true,
                        3,
                        4,
                        5,
                        6.77,
                        -2.54,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                )
        );
    }

    @Test
    void Check_X_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroLabel(
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
                        -2.54,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                )
        );
    }

    @Test
    void Check_Y_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroLabel(
                        64L,
                        "",
                        "",
                        1,
                        2,
                        true,
                        3,
                        4,
                        5,
                        6.77,
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
        OmeroLabel expectedOmeroLabel = new OmeroLabel(
                64L,
                "some type",
                "some text",
                1,
                2,
                true,
                3,
                4,
                5,
                6.77,
                -2.54,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroLabel omeroLabel = new Gson().fromJson(
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
                    "X": 6.77,
                    "Y": -2.54,
                    "omero:details:": {
                        "permissions": {
                            "isGroupWrite": false,
                            "isGroupRead": true,
                            "isGroupAnnotate": true
                        }
                    }
                }
                """,
                OmeroLabel.class
        );

        Assertions.assertEquals(expectedOmeroLabel, omeroLabel);
    }
}
