package qupath.ext.omero.core.apis.webclient.annotations;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroAnnotationExperimenter;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroCommentAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroLink;

import java.util.List;

public class TestAnnotationCreator {

    //TODO: test other annotations
    @Test
    void Check_Comment_Annotation() {
        List<Annotation> expectedAnnotations = List.of(new CommentAnnotation(
                new OmeroCommentAnnotation(
                        398L,
                        "some namespace",
                        "CommentAnnotationI",
                        new OmeroAnnotationExperimenter(43L),
                        new OmeroLink(new OmeroAnnotationExperimenter(763L)),
                        "some comment"
                ),
                List.of(
                        new OmeroSimpleExperimenter(43L, "first adder", "last adder"),
                        new OmeroSimpleExperimenter(763L, "first owner", "last owner")
                )
        ));
        String json = """
                {
                    "annotations": [
                        {
                            "id": 398,
                            "ns": "some namespace",
                            "class": "CommentAnnotationI",
                            "owner": {
                                "id": 43
                            },
                            "link": {
                                "owner": {
                                    "id": 763
                                }
                            },
                            "textValue": "some comment"
                        }
                    ],
                    "experimenters": [
                        {
                            "id": 43,
                            "firstName": "first adder",
                            "lastName": "last adder"
                        },
                        {
                            "id": 763,
                            "firstName": "first owner",
                            "lastName": "last owner"
                        }
                    ]
                }
                """;

        List<Annotation> annotations = AnnotationCreator.createAnnotations(new Gson().fromJson(json, JsonElement.class));

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedAnnotations, annotations);
    }
}
