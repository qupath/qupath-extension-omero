package qupath.ext.omero.core.apis.json.jsonentities.shapes;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;

public class TestOmeroLine {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroLine(
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
                        1d,
                        90.3,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                )
        );
    }

    @Test
    void Check_X1_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroLine(
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
                        1d,
                        90.3,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                )
        );
    }

    @Test
    void Check_Y1_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroLine(
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
                        1d,
                        90.3,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                )
        );
    }

    @Test
    void Check_X2_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroLine(
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
                        -2.54,
                        null,
                        90.3,
                        new OmeroDetails(
                                null,
                                null,
                                new OmeroPermissions(false, true, true)
                        )
                )
        );
    }

    @Test
    void Check_Y2_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroLine(
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
                        -2.54,
                        1d,
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
        OmeroLine expectedOmeroLine = new OmeroLine(
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
                1d,
                90.3,
                new OmeroDetails(
                        null,
                        null,
                        new OmeroPermissions(false, true, true)
                )
        );

        OmeroLine omeroLine = new Gson().fromJson(
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
                    "X1": 6.77,
                    "Y1": -2.54,
                    "X2": 1,
                    "Y2": 90.3,
                    "omero:details:": {
                        "permissions": {
                            "isGroupWrite": false,
                            "isGroupRead": true,
                            "isGroupAnnotate": true
                        }
                    }
                }
                """,
                OmeroLine.class
        );

        Assertions.assertEquals(expectedOmeroLine, omeroLine);
    }
}
