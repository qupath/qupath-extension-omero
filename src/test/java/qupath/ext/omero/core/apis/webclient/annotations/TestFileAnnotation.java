package qupath.ext.omero.core.apis.webclient.annotations;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.webclient.Namespace;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroAnnotationExperimenter;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroFile;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroFileAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroLink;

import java.util.List;

public class TestFileAnnotation {

    @Test
    void Check_Id() {
        long expectedId = 234;
        Annotation annotation = new FileAnnotation(
                new OmeroFileAnnotation(
                        expectedId,
                        null,
                        null,
                        null,
                        null,
                        new OmeroFile(
                                "name",
                                "mimetype",
                                345L
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
        Annotation annotation = new FileAnnotation(
                new OmeroFileAnnotation(
                        234L,
                        expectedNamespace,
                        null,
                        null,
                        null,
                        new OmeroFile(
                                "name",
                                "mimetype",
                                345L
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
        Annotation annotation = new FileAnnotation(
                new OmeroFileAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        new OmeroLink(new OmeroAnnotationExperimenter(43L)),
                        new OmeroFile(
                                "name",
                                "mimetype",
                                345L
                        )
                ),
                List.of(new OmeroSimpleExperimenter(43L, "first_adder", "last_adder"))
        );

        String adderName = annotation.getAdderName().orElseThrow();

        Assertions.assertEquals(expectedAdderName, adderName);
    }

    @Test
    void Check_Owner_Id() {
        long expectedId = 2;
        Annotation annotation = new FileAnnotation(
                new OmeroFileAnnotation(
                        234L,
                        null,
                        null,
                        new OmeroAnnotationExperimenter(2L),
                        null,
                        new OmeroFile(
                                "name",
                                "mimetype",
                                345L
                        )
                ),
                List.of(new OmeroSimpleExperimenter(2L, "first_owner", "last_owner"))
        );

        long id = annotation.getOwnerId().orElseThrow();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Owner_Name() {
        String expectedOwnerName = "first_owner last_owner";
        Annotation annotation = new FileAnnotation(
                new OmeroFileAnnotation(
                        234L,
                        null,
                        null,
                        new OmeroAnnotationExperimenter(2L),
                        null,
                        new OmeroFile(
                                "name",
                                "mimetype",
                                345L
                        )
                ),
                List.of(new OmeroSimpleExperimenter(2L, "first_owner", "last_owner"))
        );

        String ownerName = annotation.getOwnerName().orElseThrow();

        Assertions.assertEquals(expectedOwnerName, ownerName);
    }

    @Test
    void Check_File_Name() {
        String expectedFileName = "filename";
        FileAnnotation annotation = new FileAnnotation(
                new OmeroFileAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        null,
                        new OmeroFile(
                                expectedFileName,
                                "mimetype",
                                345L
                        )
                ),
                List.of()
        );

        String filename = annotation.getFilename();

        Assertions.assertEquals(expectedFileName, filename);
    }

    @Test
    void Check_Mime_Type() {
        String expectedMimeType = "mimetype";
        FileAnnotation annotation = new FileAnnotation(
                new OmeroFileAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        null,
                        new OmeroFile(
                                "name",
                                expectedMimeType,
                                345L
                        )
                ),
                List.of()
        );

        String mimeType = annotation.getMimeType();

        Assertions.assertEquals(expectedMimeType, mimeType);
    }

    @Test
    void Check_File_Size() {
        long expectedFileSize = 345L;
        FileAnnotation annotation = new FileAnnotation(
                new OmeroFileAnnotation(
                        234L,
                        null,
                        null,
                        null,
                        null,
                        new OmeroFile(
                                "name",
                                "mimetype",
                                expectedFileSize
                        )
                ),
                List.of()
        );

        long fileSize = annotation.getFileSize();

        Assertions.assertEquals(expectedFileSize, fileSize);
    }
}
