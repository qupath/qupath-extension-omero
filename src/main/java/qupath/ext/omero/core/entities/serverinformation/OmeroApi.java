package qupath.ext.omero.core.entities.serverinformation;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Optional;

/**
 * Response of the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#list-supported-versions">supported version API request</a>
 */
public class OmeroApi {

    @SerializedName("data") private List<OmeroAPIVersion> versions;
    private record OmeroAPIVersion(@SerializedName("url:base") String versionURL) {}

    @Override
    public String toString() {
        return String.format("OMERO API of versions %s", versions);
    }

    /**
     * @return the URL of the latest version of the API supported by this server,
     * or an empty Optional if it was not found
     */
    public Optional<String> getLatestVersionURL() {
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(versions.getLast().versionURL());
        }
    }
}