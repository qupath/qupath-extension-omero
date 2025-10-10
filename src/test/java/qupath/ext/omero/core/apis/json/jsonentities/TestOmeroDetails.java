package qupath.ext.omero.core.apis.json.jsonentities;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;

public class TestOmeroDetails {

    @Test
    void Check_Permissions_Required() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new OmeroDetails(
                        new OmeroExperimenter(null, 65L, null, null, null),
                        new OmeroExperimenterGroup(
                                null,
                                87L,
                                new OmeroDetails(null, null, new OmeroPermissions(false, false, false)),
                                null,
                                ""
                        ),
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroDetails expectedOmeroDetails = new OmeroDetails(
                new OmeroExperimenter(null, 65L, null, null, null),
                new OmeroExperimenterGroup(
                        null,
                        87L,
                        new OmeroDetails(null, null, new OmeroPermissions(false, true, false)),
                        null,
                        ""
                ),
                new OmeroPermissions(true, false, false)
        );

        OmeroDetails omeroDetails = new Gson().fromJson(
                """
                {
                    "owner": {
                        "@id": 65
                    },
                    "group": {
                        "@id": 87,
                        "omero:details:": {
                            "permissions": {
                            "isGroupWrite": false,
                            "isGroupRead": true,
                            "isGroupAnnotate": false
                        },
                        "url:experimenters": ""
                    },
                    "permissions": {
                        "isGroupWrite": true,
                        "isGroupRead": false,
                        "isGroupAnnotate": false
                    }
                }
                """,
                OmeroDetails.class
        );

        Assertions.assertEquals(expectedOmeroDetails, omeroDetails);
    }
}
