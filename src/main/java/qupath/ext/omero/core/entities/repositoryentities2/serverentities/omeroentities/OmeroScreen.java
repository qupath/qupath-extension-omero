package qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities;

import com.google.gson.annotations.SerializedName;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;

public record OmeroScreen(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") long id,
        @SerializedName(value = "Name") String name,
        @SerializedName(value = "Description") String description,
        @SerializedName(value = "omero:childCount") int childCount,
        @SerializedName(value = "omero:details:") OmeroDetails omeroDetails
) {
    private static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Screen";

    public OmeroScreen {
        if (!TYPE.equalsIgnoreCase(type)) {
            throw new IllegalArgumentException(String.format(
                    "The provided type %s does not correspond to the expected type %s",
                    type,
                    TYPE
            ));
        }
    }

    public Owner owner() {
        return omeroDetails == null ? null : omeroDetails.owner();
    }

    public Group group() {
        return omeroDetails == null ? null : omeroDetails.group();
    }
}
