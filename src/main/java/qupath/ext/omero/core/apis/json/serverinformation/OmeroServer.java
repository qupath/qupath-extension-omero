package qupath.ext.omero.core.apis.json.serverinformation;

import java.util.Objects;

/**
 * Represents information on an OMERO.server instance.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param host the address pointing to where the server is hosted, in regard to where the OMERO.web instance is hosted. For example,
 *             if the OMERO.server and the OMERO.web instances are installed on the same server, this field might be "localhost". Required
 * @param id a link pointing to the ID of the server. Required
 * @param port the port used by the OMERO.server instance. Required
 */
public record OmeroServer(
        String host,
        Integer id,
        Integer port
) {
    public OmeroServer {
        Objects.requireNonNull(host);
        Objects.requireNonNull(id);
        Objects.requireNonNull(port);
    }
}
