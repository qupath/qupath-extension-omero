package qupath.ext.omero.core.entities.permissions;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;

import java.util.List;

public class TestGroup {

    @Test
    void Check_Id() {
        long expectedId = 20;
        Group group = createGroup();

        long id = group.getId();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Id_When_Group_Empty() {
        long expectedId = 0;
        Group group = new Gson().fromJson("{}", Group.class);

        long id = group.getId();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Name() {
        String expectedName = "Some group";
        Group group = createGroup();

        String name = group.getName();

        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Name_When_Group_Empty() {
        String expectedName = "";
        Group group = new Gson().fromJson("{}", Group.class);

        String name = group.getName();

        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Experimenters_Link() {
        String expectedLink = "http://group.com";
        Group group = createGroup();

        String link = group.getExperimentersLink();

        Assertions.assertEquals(expectedLink, link);
    }

    @Test
    void Check_Experimenters_Link_When_Group_Empty() {
        String expectedLink = "";
        Group group = new Gson().fromJson("{}", Group.class);

        String link = group.getExperimentersLink();

        Assertions.assertEquals(expectedLink, link);
    }

    @Test
    void Check_Permission_Level() {
        PermissionLevel expectedLevel = PermissionLevel.READ_ONLY;
        Group group = createGroup();

        PermissionLevel level = group.getPermissionLevel();

        Assertions.assertEquals(expectedLevel, level);
    }

    @Test
    void Check_Permission_Level_When_Empty() {
        PermissionLevel expectedLevel = PermissionLevel.UNKNOWN;
        Group group = new Gson().fromJson("{}", Group.class);

        PermissionLevel level = group.getPermissionLevel();

        Assertions.assertEquals(expectedLevel, level);
    }

    @Test
    void Check_Owners() {
        List<Owner> expectedOwners = List.of(
                new Owner(
                        2,
                        "John",
                        "",
                        "Doe",
                        "john@doe.com",
                        "IGC",
                        "john_doe"
                ),
                new Owner(
                        3,
                        "John2",
                        "",
                        "Doe2",
                        "john2@doe.com",
                        "IGC2",
                        "john_doe2"
                )
        );
        Group group = createGroup();
        group.setOwners(expectedOwners);

        List<Owner> owners = group.getOwners();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedOwners, owners);
    }

    private Group createGroup() {
        String json = """
                {
                    "@id": 20,
                    "Name": "Some group",
                    "url:experimenters": "http://group.com",
                    "omero:details": {
                        "permissions": {
                            "isGroupWrite": false,
                            "isGroupRead": true,
                            "isGroupAnnotate": false
                        }
                    }
                }
                """;
        return new Gson().fromJson(json, Group.class);
    }
}
