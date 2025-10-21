package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestOmeroMapAnnotation {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroMapAnnotation(
                        null,
                        "namespace",
                        "type",
                        new OmeroAnnotationExperimenter(5L),
                        new OmeroLink(new OmeroAnnotationExperimenter(234L)),
                        List.of(
                                List.of("1", "s"),
                                List.of("df")
                        )
                )
        );
    }

    @Test
    void Check_Values_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroMapAnnotation(
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
        OmeroMapAnnotation expectedOmeroMapAnnotation = new OmeroMapAnnotation(
                4644L,
                "namespace",
                "type",
                new OmeroAnnotationExperimenter(5L),
                new OmeroLink(new OmeroAnnotationExperimenter(234L)),
                List.of(
                        List.of("1", "s"),
                        List.of("df")
                )
        );

        OmeroMapAnnotation omeroMapAnnotation = new Gson().fromJson(
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
                    "values": [
                        ["1", "s"],
                        ["df"]
                    ]
                }
                """,
                OmeroMapAnnotation.class
        );

        Assertions.assertEquals(expectedOmeroMapAnnotation, omeroMapAnnotation);
    }
}
