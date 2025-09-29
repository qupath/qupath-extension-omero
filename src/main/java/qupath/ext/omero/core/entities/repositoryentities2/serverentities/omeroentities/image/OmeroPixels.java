package qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.image;

import com.google.gson.annotations.SerializedName;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.lib.images.servers.PixelType;

public record OmeroPixels(
        @SerializedName(value = "SizeX") int sizeX,
        @SerializedName(value = "SizeY") int sizeY,
        @SerializedName(value = "SizeZ") int sizeZ,
        @SerializedName(value = "SizeC") int sizeC,
        @SerializedName(value = "SizeT") int sizeT,
        @SerializedName(value = "PhysicalSizeX") OmeroPhysicalSize physicalSizeX,
        @SerializedName(value = "PhysicalSizeY") OmeroPhysicalSize physicalSizeY,
        @SerializedName(value = "PhysicalSizeZ") OmeroPhysicalSize physicalSizeZ,
        @SerializedName(value = "Type") OmeroImageType imageType
) {
    public PixelType pixelType() {
        if (imageType == null || imageType.value() == null) {
            return null;
        } else {
            return ApisHandler.getPixelType(imageType.value()).orElse(null);
        }
    }

    public double sizeMebibyte() {
        PixelType pixelType = pixelType();
        if (pixelType == null) {
            return 0;
        }

        return sizeX * sizeY * sizeZ * sizeC * sizeT * pixelType.getBytesPerPixel() / (1024d * 1024d);
    }
}
