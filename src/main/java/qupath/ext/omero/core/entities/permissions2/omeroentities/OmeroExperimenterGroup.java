package qupath.ext.omero.core.entities.permissions2.omeroentities;

import com.google.gson.annotations.SerializedName;
import qupath.ext.omero.core.entities.permissions2.PermissionLevel;

public record OmeroExperimenterGroup(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") Long id,
        @SerializedName(value = "omero:details:") OmeroDetails omeroDetails,
        @SerializedName(value = "Name") String name,
        @SerializedName(value = "url:experimenters") String experimentersUrl
) {
    public boolean idNameAndExperimenterUrlDefined() {
        return id != null && name != null && experimentersUrl != null;
    }

    public PermissionLevel getPermissionLevel() {
        if (omeroDetails == null || omeroDetails.permissions() == null) {
            return PermissionLevel.UNKNOWN;
        } else {
            return omeroDetails.permissions().getPermissionLevel();
        }
    }
}
