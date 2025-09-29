package qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.image;

import com.google.gson.annotations.SerializedName;

public record OmeroPhysicalSize(
        @SerializedName(value = "Symbol") String symbol,
        @SerializedName(value = "Value") double value
) {}
