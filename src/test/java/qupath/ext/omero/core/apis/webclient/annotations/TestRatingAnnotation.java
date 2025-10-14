package qupath.ext.omero.core.apis.webclient.annotations;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.webclient.Namespace;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroAnnotationExperimenter;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroLink;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroRatingAnnotation;

import java.util.List;

public class TestRatingAnnotation {

    @Test
    void Check_Id() {
        long expectedId = 234;
        Annotation annotation = new RatingAnnotation(
                new OmeroRatingAnnotation(
                        expectedId,
                        null,
                        null,
                        null,
                        null,
                        (short) 78
                ),
                List.of()
        );

        long id = annotation.getId();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Namespace() {
        String expectedNamespace = "some namespace";
        Annotation annotation = new RatingAnnotation(
                new OmeroRatingAnnotation(
                        234L,
                        expectedNamespace,
                        null,
                        null,
                        null,
                        (short) 78
                ),
                List.of()
        );

        Namespace namespace = annotation.getNamespace().orElseThrow();

        Assertions.assertEquals(new Namespace(expectedNamespace), namespace);
    }

    @Test
    void Check_Adder_Name() {
        String expectedAdderName = "first_adder last_adder";
        Annotation annotation = new RatingAnnotation(
                new OmeroRatingAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        new OmeroLink(new OmeroAnnotationExperimenter(43L)),
                        (short) 78
                ),
                List.of(new OmeroSimpleExperimenter(43L, "first_adder", "last_adder"))
        );

        String adderName = annotation.getAdderName().orElseThrow();

        Assertions.assertEquals(expectedAdderName, adderName);
    }

    @Test
    void Check_Owner_Name() {
        String expectedOwnerName = "first_owner last_owner";
        Annotation annotation = new RatingAnnotation(
                new OmeroRatingAnnotation(
                        234L,
                        null,
                        null,
                        new OmeroAnnotationExperimenter(2L),
                        null,
                        (short) 78
                ),
                List.of(new OmeroSimpleExperimenter(2L, "first_owner", "last_owner"))
        );

        String ownerName = annotation.getOwnerName().orElseThrow();

        Assertions.assertEquals(expectedOwnerName, ownerName);
    }

    @Test
    void Check_Rating() {
        short expectedRating = 78;
        RatingAnnotation annotation = new RatingAnnotation(
                new OmeroRatingAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        null,
                        expectedRating
                ),
                List.of()
        );

        short rating = annotation.getRating();

        Assertions.assertEquals(expectedRating, rating);
    }
}
