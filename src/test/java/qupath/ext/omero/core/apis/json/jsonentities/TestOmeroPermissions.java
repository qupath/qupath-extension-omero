package qupath.ext.omero.core.apis.json.jsonentities;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.permissions.PermissionLevel;

public class TestOmeroPermissions {

    @Test
    void Check_Group_Write_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPermissions(null, false, false)
        );
    }

    @Test
    void Check_Group_Read_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPermissions(false, null, false)
        );
    }

    @Test
    void Check_Group_Annotate_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroPermissions(false, false, null)
        );
    }

    @Test
    void Check_Private_Permission_Level() {
        PermissionLevel expectedPermissionLevel = PermissionLevel.PRIVATE;
        OmeroPermissions omeroPermissions = new OmeroPermissions(
                false, false, false
        );

        PermissionLevel permissionLevel = omeroPermissions.getPermissionLevel();

        Assertions.assertEquals(expectedPermissionLevel, permissionLevel);
    }

    @Test
    void Check_Read_Only_Permission_Level() {
        PermissionLevel expectedPermissionLevel = PermissionLevel.READ_ONLY;
        OmeroPermissions omeroPermissions = new OmeroPermissions(
                false, true, false
        );

        PermissionLevel permissionLevel = omeroPermissions.getPermissionLevel();

        Assertions.assertEquals(expectedPermissionLevel, permissionLevel);
    }

    @Test
    void Check_Read_Annotate_Permission_Level() {
        PermissionLevel expectedPermissionLevel = PermissionLevel.READ_ANNOTATE;
        OmeroPermissions omeroPermissions = new OmeroPermissions(
                false, true, true
        );

        PermissionLevel permissionLevel = omeroPermissions.getPermissionLevel();

        Assertions.assertEquals(expectedPermissionLevel, permissionLevel);
    }

    @Test
    void Check_Read_Write_Permission_Level() {
        PermissionLevel expectedPermissionLevel = PermissionLevel.READ_WRITE;
        OmeroPermissions omeroPermissions = new OmeroPermissions(
                true, true, true
        );

        PermissionLevel permissionLevel = omeroPermissions.getPermissionLevel();

        Assertions.assertEquals(expectedPermissionLevel, permissionLevel);
    }

    @Test
    void Check_Created_From_Json() {
        OmeroPermissions expectedOmeroPermissions = new OmeroPermissions(true, false, true);

        OmeroPermissions omeroPermissions = new Gson().fromJson(
                """
                {
                    "isGroupWrite": true,
                    "isGroupRead": false,
                    "isGroupAnnotate": "true"
                }
                """,
                OmeroPermissions.class
        );

        Assertions.assertEquals(expectedOmeroPermissions, omeroPermissions);
    }
}
