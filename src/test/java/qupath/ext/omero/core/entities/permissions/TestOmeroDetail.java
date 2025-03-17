package qupath.ext.omero.core.entities.permissions;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroDetail {

    @Test
    void Check_Empty() {
        PermissionLevel expectedLevel = PermissionLevel.UNKNOWN;
        OmeroDetail omeroDetail = new Gson().fromJson("{}", OmeroDetail.class);

        PermissionLevel level = omeroDetail.getPermissionLevel();

        Assertions.assertEquals(expectedLevel, level);
    }

    @Test
    void Check_Permission() {
        PermissionLevel expectedLevel = PermissionLevel.READ_ANNOTATE;
        OmeroDetail omeroDetail = new Gson().fromJson(
                """
                {
                    "permissions": {
                        "isGroupWrite": false,
                        "isGroupRead": true,
                        "isGroupAnnotate": true
                    }
                }
                """,
                OmeroDetail.class
        );

        PermissionLevel level = omeroDetail.getPermissionLevel();

        Assertions.assertEquals(expectedLevel, level);
    }
}
