package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroCommentAnnotation {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroCommentAnnotation(
                        null,
                        "namespace",
                        "type",
                        new OmeroAnnotationExperimenter(5L),
                        new OmeroLink(new OmeroAnnotationExperimenter(234L)),
                        "comment"
                )
        );
    }

    @Test
    void Check_Comment_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroCommentAnnotation(
                        34534L,
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
        OmeroCommentAnnotation expectedOmeroCommentAnnotation = new OmeroCommentAnnotation(
                34534L,
                "namespace",
                "type",
                new OmeroAnnotationExperimenter(5L),
                new OmeroLink(new OmeroAnnotationExperimenter(234L)),
                "comment"
        );

        OmeroCommentAnnotation omeroCommentAnnotation = new Gson().fromJson(
                """
                {
                    "id": 34534,
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
                    "textValue": "comment"
                }
                """,
                OmeroCommentAnnotation.class
        );

        Assertions.assertEquals(expectedOmeroCommentAnnotation, omeroCommentAnnotation);
    }
}
