package qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities;

import com.google.gson.annotations.SerializedName;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.image.OmeroImage;

public record OmeroWellSample(
        @SerializedName(value = "Image") OmeroImage image,
        @SerializedName(value = "PlateAcquisition") OmeroPlateAcquisition plateAcquisition
) {}
