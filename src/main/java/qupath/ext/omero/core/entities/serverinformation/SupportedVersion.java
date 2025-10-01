package qupath.ext.omero.core.entities.serverinformation;

import com.google.gson.annotations.SerializedName;

public record SupportedVersion(
        String version,
        @SerializedName("url:base") String versionURL
) {}
