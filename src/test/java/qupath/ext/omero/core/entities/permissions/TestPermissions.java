package qupath.ext.omero.core.entities.permissions;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.permissions.PermissionLevel;

public class TestPermissions {

    @Test
    void Check_Empty() {
        PermissionLevel expectedLevel = PermissionLevel.UNKNOWN;
        Permissions permissions = new Gson().fromJson("{}", Permissions.class);

        PermissionLevel level = permissions.getPermissionLevel();

        Assertions.assertEquals(expectedLevel, level);
    }

    @Test
    void Check_Private_Permission() {
        PermissionLevel expectedLevel = PermissionLevel.PRIVATE;
        Permissions permissions = new Gson().fromJson(
                """
                {
                    "isGroupWrite": false,
                    "isGroupRead": false,
                    "isGroupAnnotate": false
                }
                """,
                Permissions.class
        );

        PermissionLevel level = permissions.getPermissionLevel();

        Assertions.assertEquals(expectedLevel, level);
    }

    @Test
    void Check_Read_Only_Permission() {
        PermissionLevel expectedLevel = PermissionLevel.READ_ONLY;
        Permissions permissions = new Gson().fromJson(
                """
                {
                    "isGroupWrite": false,
                    "isGroupRead": true,
                    "isGroupAnnotate": false
                }
                """,
                Permissions.class
        );

        PermissionLevel level = permissions.getPermissionLevel();

        Assertions.assertEquals(expectedLevel, level);
    }

    @Test
    void Check_Read_Annotate_Permission() {
        PermissionLevel expectedLevel = PermissionLevel.READ_ANNOTATE;
        Permissions permissions = new Gson().fromJson(
                """
                {
                    "isGroupWrite": false,
                    "isGroupRead": true,
                    "isGroupAnnotate": true
                }
                """,
                Permissions.class
        );

        PermissionLevel level = permissions.getPermissionLevel();

        Assertions.assertEquals(expectedLevel, level);
    }

    @Test
    void Check_Read_Write_Permission() {
        PermissionLevel expectedLevel = PermissionLevel.READ_WRITE;
        Permissions permissions = new Gson().fromJson(
                """
                {
                    "isGroupWrite": true,
                    "isGroupRead": true,
                    "isGroupAnnotate": true
                }
                """,
                Permissions.class
        );

        PermissionLevel level = permissions.getPermissionLevel();

        Assertions.assertEquals(expectedLevel, level);
    }
}
