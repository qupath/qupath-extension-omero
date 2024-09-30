package qupath.ext.omero.core.entities.permissions;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestPermissions {

    @Test
    void Check_Empty() {
        Permissions permissions = new Gson().fromJson("{}", Permissions.class);

        boolean isGroupRead = permissions.isGroupRead();

        Assertions.assertFalse(isGroupRead);
    }

    @Test
    void Check_Group_Read() {
        boolean expectedIsGroupRead = true;
        Permissions permissions = createPermissions();

        boolean isGroupRead = permissions.isGroupRead();

        Assertions.assertEquals(expectedIsGroupRead, isGroupRead);
    }

    private Permissions createPermissions() {
        String json = """
                {
                    "isGroupRead": true
                }
                """;
        return new Gson().fromJson(json, Permissions.class);
    }
}
