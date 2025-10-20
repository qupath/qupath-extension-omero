package qupath.ext.omero.core.apis.json.jsonentities.server;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * An OMERO experimenter as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter">OME specifications</a>
 * but only defined by its ID.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param id the ID of the experimenter. Required
 */
public record OmeroSimpleExperimenter(
        @SerializedName(value = "@id") Long id
) {
    public OmeroSimpleExperimenter {
        Objects.requireNonNull(id, "@id not provided");
    }
}
