package qupath.ext.omero.core.entities.permissions;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.permissions.PermissionLevel;

import java.util.Optional;

public class TestOmeroDetails {

    @Test
    void Check_Owner_Full_Name() {
        String expectedOwnerFullName = "first middle last";
        OmeroDetails omeroDetails = createFromJson();

        String ownerFullName = omeroDetails.getOwnerFullName().orElse("");

        Assertions.assertEquals(expectedOwnerFullName, ownerFullName);
    }

    @Test
    void Check_Owner_Full_Name_When_Empty() {
        OmeroDetails omeroDetails = new Gson().fromJson("{}", OmeroDetails.class);

        Optional<String> ownerFullName = omeroDetails.getOwnerFullName();

        Assertions.assertTrue(ownerFullName.isEmpty());
    }

    @Test
    void Check_Permission() {
        PermissionLevel expectedLevel = PermissionLevel.READ_ANNOTATE;
        OmeroDetails omeroDetails = createFromJson();

        PermissionLevel level = omeroDetails.getPermissionLevel();

        Assertions.assertEquals(expectedLevel, level);
    }

    @Test
    void Check_Permission_When_Empty() {
        PermissionLevel expectedLevel = PermissionLevel.UNKNOWN;
        OmeroDetails omeroDetails = new Gson().fromJson("{}", OmeroDetails.class);

        PermissionLevel level = omeroDetails.getPermissionLevel();

        Assertions.assertEquals(expectedLevel, level);
    }

    private static OmeroDetails createFromJson() {
        return new Gson().fromJson(
                """
                {
                    "owner": {
                        "FirstName": "first",
                        "MiddleName": "middle",
                        "LastName": "last"
                    },
                    "permissions": {
                        "isGroupWrite": false,
                        "isGroupRead": true,
                        "isGroupAnnotate": true
                    }
                }
                """,
                OmeroDetails.class
        );
    }
}
