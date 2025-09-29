package qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities;

import com.google.gson.annotations.SerializedName;

public record OmeroWellSample(
        @SerializedName(value = "Image") Image image,
        @SerializedName(value = "PlateAcquisition") OmeroPlateAcquisition plateAcquisition
) {}
