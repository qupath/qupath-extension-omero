package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroFileAnnotation {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroFileAnnotation(
                        null,
                        "namespace",
                        "type",
                        new OmeroAnnotationExperimenter(5L),
                        new OmeroLink(new OmeroAnnotationExperimenter(234L)),
                        new OmeroFile(
                                "name",
                                "mimetype",
                                5345L
                        )
                )
        );
    }

    @Test
    void Check_File_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroFileAnnotation(
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
        OmeroFileAnnotation expectedOmeroFileAnnotation = new OmeroFileAnnotation(
                4644L,
                "namespace",
                "type",
                new OmeroAnnotationExperimenter(5L),
                new OmeroLink(new OmeroAnnotationExperimenter(234L)),
                new OmeroFile(
                        "name",
                        "mimetype",
                        5345L
                )
        );

        OmeroFileAnnotation omeroFileAnnotation = new Gson().fromJson(
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
                    "file": {
                        "name": "name",
                        "mimetype": "mimetype",
                        "size": 5345
                    }
                }
                """,
                OmeroFileAnnotation.class
        );

        Assertions.assertEquals(expectedOmeroFileAnnotation, omeroFileAnnotation);
    }
}
