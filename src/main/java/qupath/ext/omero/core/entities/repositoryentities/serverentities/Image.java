package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.omeroentities.server.image.OmeroImage;
import qupath.ext.omero.core.entities.omeroentities.server.image.OmeroPhysicalSize;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.lib.images.servers.PixelType;

import java.net.URI;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class Image extends ServerEntity {

    private static final ResourceBundle resources = Utils.getResources();
    private final List<Attribute> attributes;
    private final PixelType pixelType;
    private final int numberOfChannels;
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

    public Image(OmeroImage omeroImage, URI webServerUri) {
        super(omeroImage.id(), omeroImage.name(), omeroImage.owner(), omeroImage.group(), webServerUri);

        this.pixelType = omeroImage.pixelType();
        this.numberOfChannels = omeroImage.sizeC();

        long acquisitionDate = omeroImage.acquisitionDate();
        int sizeX = omeroImage.sizeX();
        int sizeY = omeroImage.sizeY();
        double sizeMebibyte = omeroImage.sizeMebibyte();
        int sizeZ = omeroImage.sizeZ();
        int sizeC = omeroImage.sizeC();
        int sizeT = omeroImage.sizeT();
        OmeroPhysicalSize physicalSizeX = omeroImage.physicalSizeX();
        OmeroPhysicalSize physicalSizeY = omeroImage.physicalSizeY();
        OmeroPhysicalSize physicalSizeZ = omeroImage.physicalSizeZ();
        String imageType = omeroImage.imageType();
        this.attributes = List.of(
                new Attribute(resources.getString("Entities.Image.name"), name == null || name.isEmpty() ? "-" : name),
                new Attribute(resources.getString("Entities.Image.id"), String.valueOf(id)),
                new Attribute(
                        resources.getString("Entities.Image.owner"),
                        owner == null || owner.getFullName().isEmpty() ? "-" : owner.getFullName()
                ),
                new Attribute(
                        resources.getString("Entities.Image.group"),
                        group == null || group.getName().isEmpty() ? "-" : group.getName()
                ),
                new Attribute(
                        resources.getString("Entities.Image.acquisitionDate"),
                        acquisitionDate == 0 ? "-" : new Date(acquisitionDate).toString()
                ),
                new Attribute(resources.getString("Entities.Image.imageWidth"), sizeX < 0 ? "-" : String.format("%d px", sizeX)),
                new Attribute(resources.getString("Entities.Image.imageHeight"), sizeY < 0 ? "-" : String.format("%d px", sizeY)),
                new Attribute(
                        resources.getString("Entities.Image.uncompressedSize"),
                        sizeMebibyte <= 0 ? "-" : String.format(
                                "%.1f %s",
                                sizeMebibyte > 1000 ? sizeMebibyte / 1024 : sizeMebibyte,
                                sizeMebibyte > 1000 ? "GiB" : "MiB"
                        )
                ),
                new Attribute(resources.getString("Entities.Image.nbZSlices"), sizeZ < 0 ? "-" : String.format("%d px", sizeZ)),
                new Attribute(resources.getString("Entities.Image.nbChannels"), sizeC < 0 ? "-" : String.format("%d px", sizeC)),
                new Attribute(resources.getString("Entities.Image.nbTimePoints"), sizeT < 0 ? "-" : String.format("%d px", sizeT)),
                new Attribute(
                        resources.getString("Entities.Image.pixelSizeX"),
                        physicalSizeX == null ? "-" : String.format("%f %s", physicalSizeX.value(), physicalSizeX.symbol())
                ),
                new Attribute(
                        resources.getString("Entities.Image.pixelSizeY"),
                        physicalSizeY == null ? "-" : String.format("%f %s", physicalSizeY.value(), physicalSizeY.symbol())
                ),
                new Attribute(
                        resources.getString("Entities.Image.pixelSizeZ"),
                        physicalSizeZ == null ? "-" : String.format("%f %s", physicalSizeZ.value(), physicalSizeZ.symbol())
                ),
                new Attribute(resources.getString("Entities.Image.pixelType"), imageType == null ? "-" : imageType)
        );
    }

    @Override
    public List<Attribute> getAttributes() {
        return attributes;
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

    private boolean pixelTypeUnsupported(PixelApi pixelApi) {
        return pixelType == null || !pixelApi.canReadImage(pixelType);
    }

    private boolean numberOfChannelsUnsupported(PixelApi pixelApi) {
        return numberOfChannels < 0 || !pixelApi.canReadImage(numberOfChannels);
    }
}
