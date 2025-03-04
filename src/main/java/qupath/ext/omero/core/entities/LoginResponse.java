package qupath.ext.omero.core.entities;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.lib.io.GsonTools;

/**
 * Reads the response from a login request.
 *
 * @param group the default group of the user
 * @param userId the ID of the user
 * @param sessionUuid the session UUID of the user
 * @param isAdmin whether the user is an administrator
 */
public record LoginResponse(Group group, long userId, String sessionUuid, boolean isAdmin) {

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
                    "'eventContext' JSON object not found in %s", serverResponse
            ));
        }
        JsonObject eventContext = serverResponse.get("eventContext").getAsJsonObject();

        if (!eventContext.has("userId") || !eventContext.get("userId").isJsonPrimitive() || !eventContext.getAsJsonPrimitive("userId").isNumber()) {
            throw new IllegalArgumentException(String.format(
                    "'userId' number not found in %s", eventContext
            ));
        }
        if (!eventContext.has("sessionUuid") || !eventContext.get("sessionUuid").isJsonPrimitive()) {
            throw new IllegalArgumentException(String.format(
                    "'sessionUuid' text not found in %s", eventContext
            ));
        }
        if (!eventContext.has("isAdmin") || !eventContext.get("isAdmin").isJsonPrimitive()) {
            throw new IllegalArgumentException(String.format(
                    "'isAdmin' text not found in %s", eventContext
            ));
        }

        Group group;
        try {
            group = GsonTools.getInstance().fromJson(eventContext, Group.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        return new LoginResponse(
                group,
                eventContext.get("userId").getAsJsonPrimitive().getAsNumber().intValue(),
                eventContext.get("sessionUuid").getAsString(),
                eventContext.get("isAdmin").getAsBoolean()
        );
    }
}
