package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * An OMERO experimenter group as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#ExperimenterGroup">OME specifications</a>
 * but .
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param id the ID of the group. Required
 */
public record OmeroSimpleExperimenterGroup(
        @SerializedName(value = "@id") Long id
) {
    public OmeroSimpleExperimenterGroup {
        Objects.requireNonNull(id, "@id not provided");
    }
}
