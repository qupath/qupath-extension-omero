package qupath.ext.omero.core.entities.serverinformation;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record OmeroServers(
        @SerializedName("data") List<OmeroServer> servers
) {}
