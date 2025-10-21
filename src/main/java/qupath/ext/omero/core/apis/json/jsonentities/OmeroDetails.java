package qupath.ext.omero.core.apis.json.jsonentities;

import com.google.gson.annotations.SerializedName;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;

import java.util.Objects;

/**
 * Represents details on an OMERO entity, such as group permissions.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param experimenter an experimenter. Optional
 * @param group an experimenter group. Optional
 * @param permissions the group permission of the entity. Required
 */
public record OmeroDetails(
        @SerializedName(value = "owner") OmeroExperimenter experimenter,
        OmeroExperimenterGroup group,
        OmeroPermissions permissions
) {
    public OmeroDetails {
        Objects.requireNonNull(permissions, "permissions not provided");
    }
}
