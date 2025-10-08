package qupath.ext.omero.core.apis.json.jsonentities.server.image;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.lib.images.servers.PixelType;

import java.util.Objects;
import java.util.Optional;

/**
 * An OMERO screen as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#Pixels">OME specifications</a>.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param type a link to the specifications of this object ({@link #TYPE} is expected). Optional
 * @param sizeX the width of the image. Required
 * @param sizeY the height of the image. Required
 * @param sizeZ the number of z-stacks of the image. Required
 * @param sizeC the number of channels of the image. Required
 * @param sizeT the number of time points of the image. Required
 * @param physicalSizeX the size of pixels on the x-axis. Optional
 * @param physicalSizeY the size of pixels on the y-axis. Optional
 * @param physicalSizeZ the size of pixels on the z-axis. Optional
 * @param imageType the data type of the pixels. Required
 */
record OmeroPixels(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "SizeX") Integer sizeX,
        @SerializedName(value = "SizeY") Integer sizeY,
        @SerializedName(value = "SizeZ") Integer sizeZ,
        @SerializedName(value = "SizeC") Integer sizeC,
        @SerializedName(value = "SizeT") Integer sizeT,
        @SerializedName(value = "PhysicalSizeX") OmeroPhysicalSize physicalSizeX,
        @SerializedName(value = "PhysicalSizeY") OmeroPhysicalSize physicalSizeY,
        @SerializedName(value = "PhysicalSizeZ") OmeroPhysicalSize physicalSizeZ,
        @SerializedName(value = "Type") OmeroImageType imageType
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Pixels";
    private static final Logger logger = LoggerFactory.getLogger(OmeroPixels.class);

    public OmeroPixels {
        Objects.requireNonNull(sizeX);
        Objects.requireNonNull(sizeY);
        Objects.requireNonNull(sizeZ);
        Objects.requireNonNull(sizeC);
        Objects.requireNonNull(sizeT);
        Objects.requireNonNull(imageType);

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent pixels",
                    type,
                    TYPE
            );
        }
    }

    /**
     * Get the pixel type of this image.
     *
     * @return the pixel type of this image, or an empty Optional if the pixel type was not recognized
     */
    public Optional<PixelType> pixelType() {
        return ApisHandler.getPixelType(imageType.value());
    }

    /**
     * Get the uncompressed size of this image in MiB (Mebibyte).
     *
     * @return the uncompressed size of this image in MiB, or an empty Optional if it couldn't be computed
     */
    public Optional<Double> sizeMebibyte() {
        return pixelType().map(pixelType -> sizeX * sizeY * sizeZ * sizeC * sizeT * pixelType.getBytesPerPixel() / (1024d * 1024d));
    }
}
