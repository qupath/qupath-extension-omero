package qupath.ext.omero.core.apis.json.serverinformation;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * Represents a version of the OMERO JSON API.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param version a version number. Required
 * @param versionUrl a link pointing to where this version of the JSON API is hosted. Required
 */
public record SupportedVersion(
        String version,
        @SerializedName("url:base") String versionUrl
) {
    public SupportedVersion {
        Objects.requireNonNull(version);
        Objects.requireNonNull(versionUrl);
    }
}
