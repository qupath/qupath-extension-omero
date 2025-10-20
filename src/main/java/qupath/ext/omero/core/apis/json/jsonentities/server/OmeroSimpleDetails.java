package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * Represents a simplified version of details on an OMERO entity.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param experimenter an experimenter. Required
 * @param group an experimenter group. Required
 */
public record OmeroSimpleDetails(
        @SerializedName(value = "owner") OmeroSimpleExperimenter experimenter,
        OmeroSimpleExperimenterGroup group
) {
    public OmeroSimpleDetails {
        Objects.requireNonNull(experimenter, "owner not provided");
        Objects.requireNonNull(group, "group not provided");
    }
}
