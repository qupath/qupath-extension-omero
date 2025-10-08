package qupath.ext.omero.core.apis.json.serverinformation;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

/**
 * Represents a list of {@link OmeroServer}.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param servers a list of OMERO servers. Required
 */
public record OmeroServers(
        @SerializedName("data") List<OmeroServer> servers
) {
    public OmeroServers {
        Objects.requireNonNull(servers);
    }
}
