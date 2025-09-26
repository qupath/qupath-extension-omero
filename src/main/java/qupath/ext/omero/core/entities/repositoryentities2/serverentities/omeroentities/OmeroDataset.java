package qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities;

import com.google.gson.annotations.SerializedName;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;

public record OmeroDataset(
        @SerializedName(value = "@id") long id,
        @SerializedName(value = "Name") String name,
        @SerializedName(value = "Description") String description,
        @SerializedName(value = "omero:childCount") int childCount,
        @SerializedName(value = "omero:details:") OmeroDetails omeroDetails
) {
    public Owner owner() {
        return omeroDetails == null ? null : omeroDetails.owner();
    }

    public Group group() {
        return omeroDetails == null ? null : omeroDetails.group();
    }
}
