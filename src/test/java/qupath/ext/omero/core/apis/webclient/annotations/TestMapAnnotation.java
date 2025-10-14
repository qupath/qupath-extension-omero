package qupath.ext.omero.core.apis.webclient.annotations;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtils;
import qupath.ext.omero.core.apis.webclient.Namespace;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroAnnotationExperimenter;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroLink;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroMapAnnotation;

import java.util.List;

public class TestMapAnnotation {

    @Test
    void Check_Id() {
        long expectedId = 234;
        Annotation annotation = new MapAnnotation(
                new OmeroMapAnnotation(
                        expectedId,
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                List.of("a", "b"),
                                List.of("c"),
                                List.of("d", "e")
                        )
                ),
                List.of()
        );

        long id = annotation.getId();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Namespace() {
        String expectedNamespace = "some namespace";
        Annotation annotation = new MapAnnotation(
                new OmeroMapAnnotation(
                        234L,
                        expectedNamespace,
                        null,
                        null,
                        null,
                        List.of(
                                List.of("a", "b"),
                                List.of("c"),
                                List.of("d", "e")
                        )
                ),
                List.of()
        );

        Namespace namespace = annotation.getNamespace().orElseThrow();

        Assertions.assertEquals(new Namespace(expectedNamespace), namespace);
    }

    @Test
    void Check_Adder_Name() {
        String expectedAdderName = "first_adder last_adder";
        Annotation annotation = new MapAnnotation(
                new OmeroMapAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        new OmeroLink(new OmeroAnnotationExperimenter(43L)),
                        List.of(
                                List.of("a", "b"),
                                List.of("c"),
                                List.of("d", "e")
                        )
                ),
                List.of(new OmeroSimpleExperimenter(43L, "first_adder", "last_adder"))
        );

        String adderName = annotation.getAdderName().orElseThrow();

        Assertions.assertEquals(expectedAdderName, adderName);
    }

    @Test
    void Check_Owner_Name() {
        String expectedOwnerName = "first_owner last_owner";
        Annotation annotation = new MapAnnotation(
                new OmeroMapAnnotation(
                        234L,
                        null,
                        null,
                        new OmeroAnnotationExperimenter(2L),
                        null,
                        List.of(
                                List.of("a", "b"),
                                List.of("c"),
                                List.of("d", "e")
                        )
                ),
                List.of(new OmeroSimpleExperimenter(2L, "first_owner", "last_owner"))
        );

        String ownerName = annotation.getOwnerName().orElseThrow();

        Assertions.assertEquals(expectedOwnerName, ownerName);
    }

    @Test
    void Check_Pairs() {
        List<Pair> expectedPairs = List.of(
                new Pair("a", "b"),
                new Pair("d", "e")
        );
        MapAnnotation annotation = new MapAnnotation(
                new OmeroMapAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                List.of("a", "b"),
                                List.of("c"),
                                List.of("d", "e")
                        )
                ),
                List.of()
        );

        List<Pair> pairs = annotation.getPairs();

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedPairs, pairs);
    }
}
