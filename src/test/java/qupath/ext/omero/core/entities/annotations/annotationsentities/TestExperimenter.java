package qupath.ext.omero.core.entities.annotations.annotationsentities;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.webclient.annotations.annotationsentities.Experimenter;

public class TestExperimenter {

    @Test
    void Check_Experimenter_Id() {
        Experimenter experimenter = createExperimenter();

        int id = experimenter.id();

        Assertions.assertEquals(54, id);
    }

    @Test
    void Check_Experimenter_FirstName() {
        Experimenter experimenter = createExperimenter();

        String firstName = experimenter.firstName();

        Assertions.assertEquals("John", firstName);
    }

    @Test
    void Check_Experimenter_LastName() {
        Experimenter experimenter = createExperimenter();

        String lastName = experimenter.lastName();

        Assertions.assertEquals("Doe", lastName);
    }

    @Test
    void Check_Experimenter_FullName() {
        Experimenter experimenter = createExperimenter();

        String fullName = experimenter.fullName();

        Assertions.assertEquals("John Doe", fullName);
    }

    @Test
    void Check_Experimenter_Missing() {
        Experimenter experimenter = new Gson().fromJson("{}", Experimenter.class);

        String fullName = experimenter.fullName();

        Assertions.assertEquals("", fullName);
    }

    private Experimenter createExperimenter() {
        String json = """
                {
                    "id": 54,
                    "firstName": "John",
                    "lastName": "Doe"
                }
                """;

        return new Gson().fromJson(json, Experimenter.class);
    }
}
