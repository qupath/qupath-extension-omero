package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroTagAnnotation {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroTagAnnotation(
                        null,
                        "namespace",
                        "type",
                        new OmeroAnnotationExperimenter(5L),
                        new OmeroLink(new OmeroAnnotationExperimenter(234L)),
                        "tag"
                )
        );
    }

    @Test
    void Check_Tag_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroTagAnnotation(
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
        OmeroTagAnnotation expectedOmeroTagAnnotation = new OmeroTagAnnotation(
                4644L,
                "namespace",
                "type",
                new OmeroAnnotationExperimenter(5L),
                new OmeroLink(new OmeroAnnotationExperimenter(234L)),
                "tag"
        );

        OmeroTagAnnotation omeroTagAnnotation = new Gson().fromJson(
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
                    "textValue": "tag"
                }
                """,
                OmeroTagAnnotation.class
        );

        Assertions.assertEquals(expectedOmeroTagAnnotation, omeroTagAnnotation);
    }
}
