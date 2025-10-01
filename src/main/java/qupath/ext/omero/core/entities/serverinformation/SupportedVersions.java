package qupath.ext.omero.core.entities.serverinformation;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record SupportedVersions(
        @SerializedName("data") List<SupportedVersion> supportedVersions
) {}
