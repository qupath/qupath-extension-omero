package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroFile {

    @Test
    void Check_Name_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroFile(
                        null,
                        "mimetype",
                        464L
                )
        );
    }

    @Test
    void Check_Mimetype_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroFile(
                        "name",
                        null,
                        464L
                )
        );
    }

    @Test
    void Check_Size_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroFile(
                        "name",
                        "mimetype",
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroFile expectedOmeroFile = new OmeroFile(
                "name",
                "mimetype",
                464L
        );

        OmeroFile omeroFile = new Gson().fromJson(
                """
                {
                    "name": "name",
                    "mimetype": "mimetype",
                    "size": 464
                }
                """,
                OmeroFile.class
        );

        Assertions.assertEquals(expectedOmeroFile, omeroFile);
    }
}
