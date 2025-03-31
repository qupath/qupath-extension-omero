package qupath.ext.omero.core.entities.repositoryentities.serverentities.image;

import com.google.gson.annotations.SerializedName;

/**
 * This class contains various information related to the pixels of an image.
 *
 * @param width the width in pixels of the image, or 0 if not found
 * @param height the height in pixels of the image, or 0 if not found
 * @param sizeZ the number of z-stacks of the image, or 0 if not found
 * @param numberOfChannels the number of channels of the image, or 0 if not found
 * @param numberOfTimePoints the number of time points of the image, or 0 if not found
 * @param physicalSizeX the pixel width, or null if not found
 * @param physicalSizeY the pixel height, or null if not found
 * @param physicalSizeZ the spacing between z-slices, or null if not found
 * @param imageType the format of the pixel values (e.g. uint8), or null if not found
 */
record PixelInfo(
        @SerializedName(value = "SizeX") int width,
        @SerializedName(value = "SizeY") int height,
        @SerializedName(value = "SizeZ") int sizeZ,
        @SerializedName(value = "SizeC") int numberOfChannels,
        @SerializedName(value = "SizeT") int numberOfTimePoints,
        @SerializedName(value = "PhysicalSizeX") PhysicalSize physicalSizeX,
        @SerializedName(value = "PhysicalSizeY") PhysicalSize physicalSizeY,
        @SerializedName(value = "PhysicalSizeZ") PhysicalSize physicalSizeZ,
        @SerializedName(value = "Type") ImageType imageType
) {}
