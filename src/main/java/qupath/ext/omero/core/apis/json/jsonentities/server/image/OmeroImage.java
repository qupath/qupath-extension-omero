package qupath.ext.omero.core.apis.json.jsonentities.server.image;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.json.jsonentities.OmeroDetails;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;
import qupath.lib.images.servers.PixelType;

import java.util.Objects;
import java.util.Optional;

/**
 * An OMERO image as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#Image">OME specifications</a>.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param type a link to the specifications of this object ({@link #TYPE} is expected). Optional
 * @param id the ID of the image. Required
 * @param name the name of the image. Optional
 * @param acquisitionDate the timestamp corresponding to the time when this image was acquired. Optional
 * @param pixels information on the image. Required
 * @param omeroDetails details about this group. Optional
 */
public record OmeroImage(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") Long id,
        @SerializedName(value = "Name") String name,
        @SerializedName(value = "AcquisitionDate") Long acquisitionDate,
        @SerializedName(value = "Pixels") OmeroPixels pixels,
        @SerializedName(value = "omero:details:") OmeroDetails omeroDetails
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Image";
    private static final Logger logger = LoggerFactory.getLogger(OmeroImage.class);

    public OmeroImage {
        Objects.requireNonNull(id);
        Objects.requireNonNull(pixels);

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent an image",
                    type,
                    TYPE
            );
        }
    }

    /**
     * Get the experimenter owning this image.
     *
     * @return the experimenter owning this image, or an empty Optional if not found
     */
    public Optional<OmeroExperimenter> owner() {
        return omeroDetails == null ? Optional.empty() : Optional.ofNullable(omeroDetails.experimenter());
    }

    /**
     * Get the experimenter group owning this image.
     *
     * @return the experimenter group owning this image, or an empty Optional if not found
     */
    public Optional<OmeroExperimenterGroup> group() {
        return omeroDetails == null ? Optional.empty() : Optional.ofNullable(omeroDetails.group());
    }

    /**
     * Get the width of the image.
     *
     * @return the width of the image
     */
    public int sizeX() {
        return pixels.sizeX();
    }

    /**
     * Get the height of the image.
     *
     * @return the height of the image
     */
    public int sizeY() {
        return pixels.sizeY();
    }

    /**
     * Get the number of z-stacks of the image.
     *
     * @return the number of z-stacks of the image
     */
    public int sizeZ() {
        return pixels.sizeZ();
    }

    /**
     * Get the number of channels of the image.
     *
     * @return the number of channels of the image
     */
    public int sizeC() {
        return pixels.sizeC();
    }

    /**
     * Get the number of time points of the image.
     *
     * @return the number of time points of the image
     */
    public int sizeT() {
        return pixels.sizeT();
    }

    /**
     * Get the pixel type of this image.
     *
     * @return the pixel type of this image, or an empty Optional if the pixel type was not recognized
     */
    public Optional<PixelType> pixelType() {
        return pixels.pixelType();
    }

    /**
     * Get the uncompressed size of this image in MiB (Mebibyte).
     *
     * @return the uncompressed size of this image in MiB, or an empty Optional if it couldn't be computed
     */
    public Optional<Double> sizeMebibyte() {
        return pixels().sizeMebibyte();
    }

    /**
     * Get the pixel size on the x-axis
     *
     * @return the pixel size on the x-axis, or an empty Optional if not provided
     */
    public Optional<OmeroPhysicalSize> physicalSizeX() {
        return Optional.ofNullable(pixels.physicalSizeX());
    }

    /**
     * Get the pixel size on the y-axis
     *
     * @return the pixel size on the y-axis, or an empty Optional if not provided
     */
    public Optional<OmeroPhysicalSize> physicalSizeY() {
        return Optional.ofNullable(pixels.physicalSizeY());
    }

    /**
     * Get the pixel size on the z-axis
     *
     * @return the pixel size on the z-axis, or an empty Optional if not provided
     */
    public Optional<OmeroPhysicalSize> physicalSizeZ() {
        return Optional.ofNullable(pixels.physicalSizeZ());
    }

    /**
     * Get the data type of the pixels.
     *
     * @return the data type of the pixels
     */
    public String imageType() {
        return pixels.imageType().value();
    }
}
