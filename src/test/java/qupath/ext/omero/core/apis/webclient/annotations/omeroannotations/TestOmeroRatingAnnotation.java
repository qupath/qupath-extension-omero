package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroRatingAnnotation {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroRatingAnnotation(
                        null,
                        "namespace",
                        "type",
                        new OmeroAnnotationExperimenter(5L),
                        new OmeroLink(new OmeroAnnotationExperimenter(234L)),
                        (short) 78
                )
        );
    }

    @Test
    void Check_Value_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroRatingAnnotation(
                        4644L,
                        "namespace",
                        "type",
                        new OmeroAnnotationExperimenter(5L),
                        new OmeroLink(new OmeroAnnotationExperimenter(234L)),
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroRatingAnnotation expectedOmeroRatingAnnotation = new OmeroRatingAnnotation(
                4644L,
                "namespace",
                "type",
                new OmeroAnnotationExperimenter(5L),
                new OmeroLink(new OmeroAnnotationExperimenter(234L)),
                (short) 78
        );

        OmeroRatingAnnotation omeroRatingAnnotation = new Gson().fromJson(
                """
                {
                    "id": 4644,
                    "ns": "namespace",
                    "class": "type",
                    "owner": {
                        "id": 5
                    },
                    "link": {
                        "owner": {
                            "id": 234
                        }
                    },
                    "value": 78
                }
                """,
                OmeroRatingAnnotation.class
        );

        Assertions.assertEquals(expectedOmeroRatingAnnotation, omeroRatingAnnotation);
    }
}
