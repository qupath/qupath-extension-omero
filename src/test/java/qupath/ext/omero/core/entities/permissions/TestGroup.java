package qupath.ext.omero.core.entities.permissions;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;

import java.util.List;

public class TestGroup {

    @Test
    void Check_Id() {
        int expectedId = 20;
        Group group = createGroup();

        int id = group.getId();

        Assertions.assertEquals(expectedId, id);
    }

    @Test
    void Check_Id_When_Group_Empty() {
        int expectedId = 0;
        Group group = new Gson().fromJson("{}", Group.class);

        int id = group.getId();

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
    void Check_Private() {
        boolean expectedIsPrivate = false;
        Group group = createGroup();

        boolean isPrivate = group.isPrivate();

        Assertions.assertEquals(expectedIsPrivate, isPrivate);
    }

    @Test
    void Check_Private_When_Group_Empty() {
        boolean expectedIsPrivate = true;
        Group group = new Gson().fromJson("{}", Group.class);

        boolean isPrivate = group.isPrivate();

        Assertions.assertEquals(expectedIsPrivate, isPrivate);
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
                            "isGroupRead": true
                        }
                    }
                }
                """;
        return new Gson().fromJson(json, Group.class);
    }
}
