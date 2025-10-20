package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroImage;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroPhysicalSize;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.lib.images.servers.PixelType;

import java.net.URI;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an OMERO image.
 */
public class Image extends ServerEntity {

    private final int numberOfChannels;
    private final Date acquisitionDate;
    private final Double sizeMebibyte;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int sizeC;
    private final int sizeT;
    private final OmeroPhysicalSize physicalSizeX;
    private final OmeroPhysicalSize physicalSizeY;
    private final OmeroPhysicalSize physicalSizeZ;
    private final PixelType pixelType;
    /**
     * The reason why an image may not be supported by a pixel API
     */
    public enum UnsupportedReason {
        /**
         * The selected pixel API does not support images with this number of channels
         */
        NUMBER_OF_CHANNELS,
        /**
         * The selected pixel API does not support images with this pixel type
         */
        PIXEL_TYPE,
        /**
         * The selected pixel API is not available
         */
        PIXEL_API_UNAVAILABLE
    }

    /**
     * Create an image from an {@link OmeroImage}.
     *
     * @param omeroImage the OMERO image to create the image from
     * @param webServerUri the URI of the web server owning this entity
     * @throws NullPointerException if one of the provided parameters is null
     */
    public Image(OmeroImage omeroImage, URI webServerUri) {
        super(
                omeroImage.id(),
                omeroImage.name(),
                omeroImage.omeroDetails().experimenter().id(),
                omeroImage.omeroDetails().group().id(),
                webServerUri
        );

        this.numberOfChannels = omeroImage.pixels().sizeC();
        this.acquisitionDate = omeroImage.acquisitionDate() == null ? null : new Date(omeroImage.acquisitionDate());
        this.sizeMebibyte = omeroImage.pixels().sizeMebibyte().orElse(null);
        this.sizeX = omeroImage.pixels().sizeX();
        this.sizeY = omeroImage.pixels().sizeY();
        this.sizeZ = omeroImage.pixels().sizeZ();
        this.sizeC = omeroImage.pixels().sizeC();
        this.sizeT = omeroImage.pixels().sizeT();
        this.physicalSizeX = omeroImage.pixels().physicalSizeX();
        this.physicalSizeY = omeroImage.pixels().physicalSizeY();
        this.physicalSizeZ = omeroImage.pixels().physicalSizeZ();
        this.pixelType = omeroImage.pixels().pixelType().orElse(null);
    }

    @Override
    public boolean hasChildren() {
        return false;
    }

    @Override
    public CompletableFuture<? extends List<? extends RepositoryEntity>> getChildren(long ownerId, long groupId) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public String getLabel() {
        return name == null ? String.format("Image %d", id) : name;
    }

    @Override
    public String toString() {
        return String.format("Image %s of ID %d", name, id);
    }

    /**
     * Indicate whether this image can be open with the provided pixel API.
     *
     * @param pixelApi the pixel API that could potentially open this image. Can be null, in which case false is returned
     * @return whether this image can be open with the provided pixel API
     */
    public synchronized boolean isSupported(PixelApi pixelApi) {
        if (pixelApi == null) {
            return false;
        }

        if (pixelTypeUnsupported(pixelApi)) {
            return false;
        }

        return !numberOfChannelsUnsupported(pixelApi);
    }

    /**
     * Indicate the reasons why this image is potentially unsupported by the provided pixel API.
     *
     * @param pixelApi the pixel API that could potentially open this image. Can be null, in which case
     *                 {@link UnsupportedReason#PIXEL_API_UNAVAILABLE} is returned
     * @return a set of reasons of why this image is unsupported by the provided pixel API (which is empty
     * if this image is actually supported)
     */
    public synchronized Set<UnsupportedReason> getUnsupportedReasons(PixelApi pixelApi) {
        if (pixelApi == null) {
            return EnumSet.of(UnsupportedReason.PIXEL_API_UNAVAILABLE);
        }

        EnumSet<UnsupportedReason> unsupportedReasons = EnumSet.noneOf(UnsupportedReason.class);

        if (pixelTypeUnsupported(pixelApi)) {
            unsupportedReasons.add(UnsupportedReason.PIXEL_TYPE);
        }

        if (numberOfChannelsUnsupported(pixelApi)) {
            unsupportedReasons.add(UnsupportedReason.NUMBER_OF_CHANNELS);
        }

        return unsupportedReasons;
    }

    /**
     * @return the date corresponding to when this image was acquired, or an empty Optional if not provided
     */
    public Optional<Date> getAcquisitionDate() {
        return Optional.ofNullable(acquisitionDate);
    }

    /**
     * @return the uncompressed size of this image in MiB, or an empty Optional if it couldn't be computed
     */
    public Optional<Double> getSizeMebibyte() {
        return Optional.ofNullable(sizeMebibyte);
    }

    /**
     * @return the width of this image
     */
    public int getSizeX() {
        return sizeX;
    }

    /**
     * @return the height of this image
     */
    public int getSizeY() {
        return sizeY;
    }

    /**
     * @return the number of z-stacks of this image
     */
    public int getSizeZ() {
        return sizeZ;
    }

    /**
     * @return the number of channels of this image
     */
    public int getSizeC() {
        return sizeC;
    }

    /**
     * @return the number of timepoints of this image
     */
    public int getSizeT() {
        return sizeT;
    }

    /**
     * @return the pixel size on the x-axis, or an empty Optional if not provided
     */
    public Optional<OmeroPhysicalSize> getPhysicalSizeX() {
        return Optional.ofNullable(physicalSizeX);
    }

    /**
     * @return the pixel size on the y-axis, or an empty Optional if not provided
     */
    public Optional<OmeroPhysicalSize> getPhysicalSizeY() {
        return Optional.ofNullable(physicalSizeY);
    }

    /**
     * @return the pixel size on the z-axis, or an empty Optional if not provided
     */
    public Optional<OmeroPhysicalSize> getPhysicalSizeZ() {
        return Optional.ofNullable(physicalSizeZ);
    }

    /**
     * @return the pixel type of this image, or an empty Optional if the pixel type was not recognized
     */
    public Optional<PixelType> getPixelType() {
        return Optional.ofNullable(pixelType);
    }

    private boolean pixelTypeUnsupported(PixelApi pixelApi) {
        return pixelType == null || !pixelApi.canReadImage(pixelType);
    }

    private boolean numberOfChannelsUnsupported(PixelApi pixelApi) {
        return numberOfChannels < 0 || !pixelApi.canReadImage(numberOfChannels);
    }
}
