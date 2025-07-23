package qupath.ext.omero.core.entities.repositoryentities.serverentities.image;

import com.google.gson.annotations.SerializedName;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;

import java.util.Date;
import java.util.EnumSet;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Provides some information on an OMERO image.
 * <p>
 * This class uses the {@link PixelInfo} class to get information about pixels.
 */
public class Image extends ServerEntity {

    private static final Logger logger = LoggerFactory.getLogger(Image.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Entities.Image.name"),
            resources.getString("Entities.Image.id"),
            resources.getString("Entities.Image.owner"),
            resources.getString("Entities.Image.group"),
            resources.getString("Entities.Image.acquisitionDate"),
            resources.getString("Entities.Image.imageWidth"),
            resources.getString("Entities.Image.imageHeight"),
            resources.getString("Entities.Image.uncompressedSize"),
            resources.getString("Entities.Image.nbZSlices"),
            resources.getString("Entities.Image.nbChannels"),
            resources.getString("Entities.Image.nbTimePoints"),
            resources.getString("Entities.Image.pixelSizeX"),
            resources.getString("Entities.Image.pixelSizeY"),
            resources.getString("Entities.Image.pixelSizeZ"),
            resources.getString("Entities.Image.pixelType")
    };
    @SerializedName(value = "AcquisitionDate") private long acquisitionDate;
    @SerializedName(value = "Pixels") private PixelInfo pixels;
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
     * Creates an empty image only defined by its ID.
     */
    public Image(long id) {
        this.id = id;
    }

    @Override
    public boolean hasChildren() {
        return false;
    }

    @Override
    public ObservableList<? extends RepositoryEntity> getChildren() {
        return FXCollections.emptyObservableList();
    }

    @Override
    public String getLabel() {
        return name == null ? String.format("Image %d", id) : name;
    }

    @Override
    public boolean isPopulatingChildren() {
        return false;
    }

    @Override
    public String getAttributeName(int informationIndex) {
        if (informationIndex < ATTRIBUTES.length) {
            return ATTRIBUTES[informationIndex];
        } else {
            return "";
        }
    }

    @Override
    public String getAttributeValue(int informationIndex) {
        return switch (informationIndex) {
            case 0 -> name == null || name.isEmpty() ? "-" : name;
            case 1 -> String.valueOf(getId());
            case 2 -> getOwner().getFullName();
            case 3 -> getGroupName();
            case 4 -> acquisitionDate == 0 ? "-" : new Date(acquisitionDate).toString();
            case 5 -> Optional.ofNullable(pixels)
                    .map(PixelInfo::width)
                    .map(width -> String.format("%d px", width))
                    .orElse("-");
            case 6 -> Optional.ofNullable(pixels)
                    .map(PixelInfo::height)
                    .map(height -> String.format("%d px", height))
                    .orElse("-");
            case 7 -> {
                var pixelsOptional = Optional.ofNullable(pixels);
                var pixelType = pixelsOptional.map(PixelInfo::imageType).map(ImageType::value);

                if (pixelsOptional.isPresent() && pixelType.isPresent()) {
                    var quPathPixelType = ApisHandler.getPixelType(pixelType.get());

                    if (quPathPixelType.isPresent()) {
                        double size = pixelsOptional.get().width() / 1024.0 * pixelsOptional.get().height() / 1024.0 *
                                quPathPixelType.get().getBytesPerPixel() * pixelsOptional.get().numberOfChannels() *
                                pixelsOptional.get().sizeZ() * pixelsOptional.get().numberOfTimePoints();
                        String unit = "MB";
                        if (size > 1000) {
                            size /= 1024;
                            unit = "GB";
                        }
                        yield String.format("%.1f %s", size, unit);
                    }
                }
                yield "-";
            }
            case 8 -> Optional.ofNullable(pixels)
                    .map(PixelInfo::sizeZ)
                    .map(String::valueOf)
                    .orElse("-");
            case 9 -> Optional.ofNullable(pixels)
                    .map(PixelInfo::numberOfChannels)
                    .map(String::valueOf)
                    .orElse("-");
            case 10 -> Optional.ofNullable(pixels)
                    .map(PixelInfo::numberOfTimePoints)
                    .map(String::valueOf)
                    .orElse("-");
            case 11 -> Optional.ofNullable(pixels)
                    .map(PixelInfo::physicalSizeX)
                    .map(x -> x.value() + " " + x.symbol())
                    .orElse("-");
            case 12 -> Optional.ofNullable(pixels)
                    .map(PixelInfo::physicalSizeY)
                    .map(y -> y.value() + " " + y.symbol())
                    .orElse("-");
            case 13 -> Optional.ofNullable(pixels)
                    .map(PixelInfo::physicalSizeZ)
                    .map(z -> z.value() + " " + z.symbol())
                    .orElse("-");
            case 14 -> Optional.ofNullable(pixels).map(PixelInfo::imageType).map(ImageType::value).orElse("-");
            default -> "";
        };
    }

    @Override
    public int getNumberOfAttributes() {
        return ATTRIBUTES.length;
    }

    @Override
    public String toString() {
        return String.format("Image %s of ID %d", name, id);
    }

    /**
     * Indicates if an OMERO entity type refers to an image
     *
     * @param type the OMERO entity type
     * @return whether this type refers to an image
     */
    public static boolean isImage(String type) {
        return "http://www.openmicroscopy.org/Schemas/OME/2016-06#Image".equalsIgnoreCase(type) || "Image".equalsIgnoreCase(type);
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

        if (!Optional.ofNullable(pixels)
                .map(PixelInfo::imageType)
                .map(ImageType::value)
                .flatMap(ApisHandler::getPixelType)
                .map(pixelApi::canReadImage)
                .orElse(false)
        ) {
            return false;
        }

        return Optional.ofNullable(pixels)
                .map(PixelInfo::numberOfChannels)
                .map(pixelApi::canReadImage)
                .orElse(false);
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

        if (!Optional.ofNullable(pixels)
                .map(PixelInfo::imageType)
                .map(ImageType::value)
                .flatMap(ApisHandler::getPixelType)
                .map(pixelApi::canReadImage)
                .orElse(false)
        ) {
            unsupportedReasons.add(UnsupportedReason.PIXEL_TYPE);
        }

        if (!Optional.ofNullable(pixels)
                .map(PixelInfo::numberOfChannels)
                .map(pixelApi::canReadImage)
                .orElse(false)
        ) {
            unsupportedReasons.add(UnsupportedReason.NUMBER_OF_CHANNELS);
        }

        return unsupportedReasons;
    }
}
