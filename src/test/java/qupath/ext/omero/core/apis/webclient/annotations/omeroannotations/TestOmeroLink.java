package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroLink {

    @Test
    void Check_Owner_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroLink(
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroLink expectedOmeroLink = new OmeroLink(
                new OmeroAnnotationExperimenter(
                        43L
                )
        );

        OmeroLink omeroLink = new Gson().fromJson(
                """
                {
                    "owner": {
                        "id": 43
                    }
                }
                """,
                OmeroLink.class
        );

        Assertions.assertEquals(expectedOmeroLink, omeroLink);
    }
}
