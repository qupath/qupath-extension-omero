package qupath.ext.omero.core.apis.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents information contained in a successful login response.
 *
 * @param groupId the ID of the default group of the user
 * @param userId the ID of the user
 * @param sessionUuid the session UUID of the user
 * @param isAdmin whether the user is an administrator
 * @param ownedGroupIds the IDs of all groups the user is an owner (or leader) of
 */
public record LoginResponse(long groupId, long userId, String sessionUuid, boolean isAdmin, List<Long> ownedGroupIds) {

    /**
     * Parse a server authentication response.
     *
     * @param serverResponse the raw server response of the authentication request
     * @return the parsed login response
     * @throws IllegalArgumentException when the server response doesn't contain the required elements
     */
    public static LoginResponse parseServerAuthenticationResponse(JsonObject serverResponse) {
        if (!serverResponse.has("eventContext") || !serverResponse.get("eventContext").isJsonObject()) {
            throw new IllegalArgumentException(String.format(
                    "'eventContext' JSON object not found in %s. Cannot parse login response", serverResponse
            ));
        }
        JsonObject eventContext = serverResponse.get("eventContext").getAsJsonObject();

        if (!eventContext.has("groupId") || !eventContext.get("groupId").isJsonPrimitive() || !eventContext.getAsJsonPrimitive("groupId").isNumber()) {
            throw new IllegalArgumentException(String.format(
                    "'groupId' number not found in %s. Cannot set ID of user group", eventContext
            ));
        }
        if (!eventContext.has("userId") || !eventContext.get("userId").isJsonPrimitive() || !eventContext.getAsJsonPrimitive("userId").isNumber()) {
            throw new IllegalArgumentException(String.format(
                    "'userId' number not found in %s. Cannot set ID of connected user", eventContext
            ));
        }
        if (!eventContext.has("sessionUuid") || !eventContext.get("sessionUuid").isJsonPrimitive()) {
            throw new IllegalArgumentException(String.format(
                    "'sessionUuid' text not found in %s. Cannot set session UUID", eventContext
            ));
        }
        if (!eventContext.has("isAdmin") || !eventContext.get("isAdmin").isJsonPrimitive()) {
            throw new IllegalArgumentException(String.format(
                    "'isAdmin' text not found in %s. Cannot determine if the connected user is an admin", eventContext
            ));
        }

        if (!eventContext.has("leaderOfGroups") || !eventContext.get("leaderOfGroups").isJsonArray()) {
            throw new IllegalArgumentException(String.format(
                    "'leaderOfGroups' array not found in %s. Cannot find the IDs of all groups the connected user is an owner of", eventContext
            ));
        }

        return new LoginResponse(
                eventContext.get("groupId").getAsInt(),
                eventContext.get("userId").getAsInt(),
                eventContext.get("sessionUuid").getAsString(),
                eventContext.get("isAdmin").getAsBoolean(),
                getOwnedGroupIds(eventContext.get("leaderOfGroups").getAsJsonArray())
        );
    }

    private static List<Long> getOwnedGroupIds(JsonArray leaderOfGroups) {
        List<Long> ownedGroupIds = new ArrayList<>();

        for (JsonElement jsonElement: leaderOfGroups) {
            if (!jsonElement.isJsonPrimitive()) {
                throw new IllegalArgumentException(String.format(
                        "The %s element in %s is not a JSON primitive. Cannot find the IDs of all groups the connected user is an owner of",
                        jsonElement,
                        leaderOfGroups
                ));
            }
            try {
                ownedGroupIds.add(jsonElement.getAsLong());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(e);
            }
        }

        return ownedGroupIds;
    }
}
