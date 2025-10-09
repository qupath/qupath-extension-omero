package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

//TODO: test other classes of package
public class TestOmeroAnnotationExperimenter {

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new OmeroAnnotationExperimenter(
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroAnnotationExperimenter expectedOmeroAnnotationExperimenter = new OmeroAnnotationExperimenter(
                234L
        );

        OmeroAnnotationExperimenter omeroAnnotationExperimenter = new Gson().fromJson(
                """
                {
                    "id": 234
                }
                """,
                OmeroAnnotationExperimenter.class
        );

        Assertions.assertEquals(expectedOmeroAnnotationExperimenter, omeroAnnotationExperimenter);
    }
}
