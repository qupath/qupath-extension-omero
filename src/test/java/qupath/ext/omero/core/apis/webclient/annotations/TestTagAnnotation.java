package qupath.ext.omero.core.apis.webclient.annotations;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.webclient.Namespace;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroAnnotationExperimenter;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroLink;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroTagAnnotation;

import java.util.List;

public class TestTagAnnotation {

    @Test
    void Check_Id() {
        long expectedId = 234;
        Annotation annotation = new TagAnnotation(
                new OmeroTagAnnotation(
                        expectedId,
                        null,
                        null,
                        null,
                        null,
                        "tag"
                ),
                List.of()
        );

        long id = annotation.getId();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Namespace() {
        String expectedNamespace = "some namespace";
        Annotation annotation = new TagAnnotation(
                new OmeroTagAnnotation(
                        234L,
                        expectedNamespace,
                        null,
                        null,
                        null,
                        "tag"
                ),
                List.of()
        );

        Namespace namespace = annotation.getNamespace().orElseThrow();

        Assertions.assertEquals(new Namespace(expectedNamespace), namespace);
    }

    @Test
    void Check_Adder_Name() {
        String expectedAdderName = "first_adder last_adder";
        Annotation annotation = new TagAnnotation(
                new OmeroTagAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        new OmeroLink(new OmeroAnnotationExperimenter(43L)),
                        "tag"
                ),
                List.of(new OmeroSimpleExperimenter(43L, "first_adder", "last_adder"))
        );

        String adderName = annotation.getAdderName().orElseThrow();

        Assertions.assertEquals(expectedAdderName, adderName);
    }

    @Test
    void Check_Owner_Id() {
        long expectedId = 2;
        Annotation annotation = new TagAnnotation(
                new OmeroTagAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        new OmeroLink(new OmeroAnnotationExperimenter(43L)),
                        "tag"
                ),
                List.of(new OmeroSimpleExperimenter(43L, "first_adder", "last_adder"))
        );

        long id = annotation.getOwnerId().orElseThrow();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Owner_Name() {
        String expectedOwnerName = "first_owner last_owner";
        Annotation annotation = new TagAnnotation(
                new OmeroTagAnnotation(
                        234L,
                        null,
                        null,
                        new OmeroAnnotationExperimenter(2L),
                        null,
                        "tag"
                ),
                List.of(new OmeroSimpleExperimenter(2L, "first_owner", "last_owner"))
        );

        String ownerName = annotation.getOwnerName().orElseThrow();

        Assertions.assertEquals(expectedOwnerName, ownerName);
    }

    @Test
    void Check_Tag() {
        String expectedTag = "tag";
        TagAnnotation annotation = new TagAnnotation(
                new OmeroTagAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        null,
                        expectedTag
                ),
                List.of()
        );

        String tag = annotation.getTag();

        Assertions.assertEquals(expectedTag, tag);
    }
}
