package qupath.ext.omero.core.entities.serverinformation;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Response of the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#list-available-omero-servers">server list API request</a>
 */
public class OmeroServerList {

    @SerializedName("data") private List<OmeroServerInfo> serverInfos;
    private record OmeroServerInfo(
            @SerializedName("id") int id,
            @SerializedName("host") String host,
            @SerializedName("port") int port
    ) {}

    @Override
    public String toString() {
        return String.format("OMERO server list: %s", serverInfos);
    }

    /**
     * @return the ID of the first server we can connect to,
     * or an empty Optional if it was not found
     */
    public OptionalInt getServerId() {
        if (serverInfos == null || serverInfos.isEmpty()) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(serverInfos.getFirst().id());
        }
    }

    /**
     * @return the URL of the first server we can connect to,
     * or an empty Optional if it was not found
     */
    public Optional<String> getServerHost() {
        if (serverInfos == null || serverInfos.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(serverInfos.getFirst().host());
        }
    }

    /**
     * @return the port of the first server we can connect to,
     * or an empty Optional if it was not found
     */
    public OptionalInt getServerPort() {
        if (serverInfos == null || serverInfos.isEmpty()) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(serverInfos.getFirst().port());
        }
    }
}