package qupath.ext.omero.core.entities.annotations.annotationsentities;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.entities.permissions.Owner;

public class TestLink {

    @Test
    void Check_Link_Owner() {
        Owner expectedOwner = new Owner(10, "John", "", "Doe", "john.doe@qupath.com", "UoE", "john_doe");
        Link link = createLink(expectedOwner);

        Owner actualOwner = link.owner();

        Assertions.assertEquals(expectedOwner, actualOwner);
    }

    @Test
    void Check_Link_Missing() {
        Link link = new Gson().fromJson("{}", Link.class);

        Owner owner = link.owner();

        Assertions.assertNull(owner);
    }

    private Link createLink(Owner owner) {
        String json = String.format("""
                {
                    "owner": %s
                }
                """, new Gson().toJson(owner));

        return new Gson().fromJson(json, Link.class);
    }
}
