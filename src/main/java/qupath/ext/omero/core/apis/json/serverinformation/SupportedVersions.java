package qupath.ext.omero.core.apis.json.serverinformation;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

/**
 * Represents a list of {@link SupportedVersion}.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param supportedVersions a list of supported versions. Required
 */
public record SupportedVersions(
        @SerializedName("data") List<SupportedVersion> supportedVersions
) {
    public SupportedVersions {
        Objects.requireNonNull(supportedVersions);
    }
}
