package qupath.ext.omero.core.apis.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtils;

import java.util.List;

public class TestLoginResponse {

    @Test
    void Check_Empty_Response() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> LoginResponse.parseServerAuthenticationResponse(new JsonObject()));
    }

    @Test
    void Check_Successful_Response() {
        JsonObject serverResponse = getServerResponse();

        Assertions.assertDoesNotThrow(() -> LoginResponse.parseServerAuthenticationResponse(serverResponse));
    }

    @Test
    void Check_User_ID() {
        JsonObject serverResponse = getServerResponse();
        int expectedId = 15;

        LoginResponse loginResponse = LoginResponse.parseServerAuthenticationResponse(serverResponse);

        Assertions.assertEquals(expectedId, loginResponse.userId());
    }

    @Test
    void Check_Session_UUID() {
        JsonObject serverResponse = getServerResponse();
        String expectedSessionUuid = "86cdf82c-8df9-11ee-b9d1-0242ac120002";

        LoginResponse loginResponse = LoginResponse.parseServerAuthenticationResponse(serverResponse);

        Assertions.assertEquals(expectedSessionUuid, loginResponse.sessionUuid());
    }

    @Test
    void Check_Group_ID() {
        JsonObject serverResponse = getServerResponse();
        long expectedGroupId = 54;

        LoginResponse loginResponse = LoginResponse.parseServerAuthenticationResponse(serverResponse);

        Assertions.assertEquals(expectedGroupId, loginResponse.groupId());
    }

    @Test
    void Check_Is_Admin() {
        JsonObject serverResponse = getServerResponse();
        boolean expectedIsAdmin = false;

        LoginResponse loginResponse = LoginResponse.parseServerAuthenticationResponse(serverResponse);

        Assertions.assertEquals(expectedIsAdmin, loginResponse.isAdmin());
    }

    @Test
    void Check_Owned_Group_Ids() {
        JsonObject serverResponse = getServerResponse();
        List<Long> expectedOwnedGroupIds = List.of(354L, 675L);

        LoginResponse loginResponse = LoginResponse.parseServerAuthenticationResponse(serverResponse);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedOwnedGroupIds, loginResponse.ownedGroupIds());
    }

    private static JsonObject getServerResponse() {
        return JsonParser.parseString(
                """
                {
                    "eventContext": {
                        "userId": 15,
                        "userName": "username",
                        "@id": 54,
                        "sessionUuid": "86cdf82c-8df9-11ee-b9d1-0242ac120002",
                        "isAdmin": False,
                        "leaderOfGroups": [354, 675]
                    }
                }
                """
        ).getAsJsonObject();
    }
}
