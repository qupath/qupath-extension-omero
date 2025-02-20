package qupath.ext.omero.core.entities.permissions;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroDetail {

    @Test
    void Check_Empty() {
        OmeroDetail omeroDetail = new Gson().fromJson("{}", OmeroDetail.class);

        boolean canReadGroup = omeroDetail.canReadGroup();

        Assertions.assertFalse(canReadGroup);
    }

    @Test
    void Check_Latest_Version_URL() {
        boolean expectedCanReadGroup = true;
        OmeroDetail omeroDetail = createOmeroDetail();

        boolean canReadGroup = omeroDetail.canReadGroup();

        Assertions.assertEquals(expectedCanReadGroup, canReadGroup);
    }

    private OmeroDetail createOmeroDetail() {
        String json = """
                {
                    "permissions": {
                        "isGroupRead": true
                    }
                }
                """;
        return new Gson().fromJson(json, OmeroDetail.class);
    }
}
