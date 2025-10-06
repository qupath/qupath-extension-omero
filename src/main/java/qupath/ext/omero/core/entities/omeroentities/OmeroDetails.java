package qupath.ext.omero.core.entities.omeroentities;

import com.google.gson.annotations.SerializedName;
import qupath.ext.omero.core.entities.permissions.Experimenter;
import qupath.ext.omero.core.entities.permissions.ExperimenterGroup;

import java.util.Objects;

/**
 * Represents details on an OMERO entity, such as group permissions.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param experimenter an experimenter. Optional
 * @param group an experimenter group. Optional
 * @param permissions the group permission of the entity. Required
 */
public record OmeroDetails(
        @SerializedName(value = "owner") Experimenter experimenter,
        @SerializedName(value = "group") ExperimenterGroup group,
        OmeroPermissions permissions
) {
    public OmeroDetails {
        Objects.requireNonNull(permissions);
    }
}
