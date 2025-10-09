package qupath.ext.omero.core.apis.webclient.annotations;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.webclient.Namespace;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroAnnotationExperimenter;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroCommentAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroLink;

import java.util.List;

//TODO: test other annotations
public class TestCommentAnnotation {

    @Test
    void Check_Id() {
        long expectedId = 234;
        Annotation annotation = new CommentAnnotation(
                new OmeroCommentAnnotation(
                        expectedId,
                        null,
                        null,
                        null,
                        null,
                        "some comment"
                ),
                List.of()
        );

        long id = annotation.getId();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Namespace() {
        String expectedNamespace = "some namespace";
        Annotation annotation = new CommentAnnotation(
                new OmeroCommentAnnotation(
                        234L,
                        expectedNamespace,
                        null,
                        null,
                        null,
                        "some comment"
                ),
                List.of()
        );

        Namespace namespace = annotation.getNamespace().orElseThrow();

        Assertions.assertEquals(new Namespace(expectedNamespace), namespace);
    }

    @Test
    void Check_Adder_Name() {
        String expectedAdderName = "first_adder last_adder";
        Annotation annotation = new CommentAnnotation(
                new OmeroCommentAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        new OmeroLink(new OmeroAnnotationExperimenter(43L)),
                        "some comment"
                ),
                List.of(new OmeroSimpleExperimenter(43L, "first_adder", "last_adder"))
        );

        String adderName = annotation.getAdderName().orElseThrow();

        Assertions.assertEquals(expectedAdderName, adderName);
    }

    @Test
    void Check_Owner_Name() {
        String expectedOwnerName = "first_owner last_owner";
        Annotation annotation = new CommentAnnotation(
                new OmeroCommentAnnotation(
                        234L,
                        null,
                        null,
                        new OmeroAnnotationExperimenter(2L),
                        null,
                        "some comment"
                ),
                List.of(new OmeroSimpleExperimenter(2L, "first_owner", "last_owner"))
        );

        String ownerName = annotation.getOwnerName().orElseThrow();

        Assertions.assertEquals(expectedOwnerName, ownerName);
    }

    @Test
    void Check_Comment() {
        String expectedComment = "some comment";
        CommentAnnotation annotation = new CommentAnnotation(
                new OmeroCommentAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        null,
                        expectedComment
                ),
                List.of()
        );

        String comment = annotation.getComment();

        Assertions.assertEquals(expectedComment, comment);
    }
}
