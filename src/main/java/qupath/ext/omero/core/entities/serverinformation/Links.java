package qupath.ext.omero.core.entities.serverinformation;

import com.google.gson.annotations.SerializedName;

public record Links(
        @SerializedName("url:experimenters") String owners,
        @SerializedName("url:experimentergroups") String groups,
        @SerializedName("url:projects") String projects,
        @SerializedName("url:datasets") String datasets,
        @SerializedName("url:images") String images,
        @SerializedName("url:screens") String screens,
        @SerializedName("url:plates") String plates,
        @SerializedName("url:token") String token,
        @SerializedName("url:servers") String servers,
        @SerializedName("url:login") String login
) {
    public boolean allLinksDefined() {
        return owners != null && !owners.isBlank() &&
                groups != null && !groups.isBlank() &&
                projects != null && !projects.isBlank() &&
                datasets != null && !datasets.isBlank() &&
                images != null && !images.isBlank() &&
                screens != null && !screens.isBlank() &&
                plates != null && !plates.isBlank() &&
                token != null && !token.isBlank() &&
                servers != null && !servers.isBlank() &&
                login != null && !login.isBlank();
    }
}
