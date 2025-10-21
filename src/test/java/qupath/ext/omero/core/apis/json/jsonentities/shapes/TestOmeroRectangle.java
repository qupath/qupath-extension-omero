package qupath.ext.omero.core.apis.json.jsonentities.shapes;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroPermissions;

public class TestOmeroRectangle {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroRectangle(
                        null,
                        "",
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
    void Check_X_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroRectangle(
                        64L,
                        "",
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
    void Check_Y_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroRectangle(
                        64L,
                        "",
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
    void Check_Width_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroRectangle(
                        64L,
                        "",
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
    void Check_Height_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroRectangle(
                        64L,
                        "",
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
        OmeroRectangle expectedOmeroRectangle = new OmeroRectangle(
                64L,
                "23:44",
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

        OmeroRectangle omeroRectangle = new Gson().fromJson(
                """
                {
                    "@id": 64,
                    "oldId": "23:44",
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
                    "Width": 1,
                    "Height": 90.3,
                    "omero:details": {
                        "permissions": {
                            "isGroupWrite": false,
                            "isGroupRead": true,
                            "isGroupAnnotate": true
                        }
                    }
                }
                """,
                OmeroRectangle.class
        );

        Assertions.assertEquals(expectedOmeroRectangle, omeroRectangle);
    }
}
