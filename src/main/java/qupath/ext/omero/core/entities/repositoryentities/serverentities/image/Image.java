package qupath.ext.omero.core.entities.repositoryentities.serverentities.image;

import com.google.gson.annotations.SerializedName;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
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
    private transient EnumSet<UnsupportedReason> unsupportedReasons;
    private transient BooleanProperty isSupported;
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
     * @return whether this image can be opened within QuPath. This property may be updated
     * from any thread
     * @throws IllegalStateException when the web server URI has not been set
     */
    public ReadOnlyBooleanProperty isSupported() {
        setUpSupported();
        return isSupported;
    }

    /**
     * @return the reasons why this image is unsupported (empty if this image is supported)
     * @throws IllegalStateException when the web server URI has not been set
     */
    public synchronized Set<UnsupportedReason> getUnsupportedReasons() {
        setUpSupported();
        return unsupportedReasons;
    }

    private synchronized void setUpSupported() {
        if (isSupported == null) {
            if (webServerURI == null) {
                throw new IllegalStateException("The web server URI has not been set on this image. Cannot check if supported");
            }

            isSupported = new SimpleBooleanProperty(false);
            unsupportedReasons = EnumSet.noneOf(UnsupportedReason.class);

            Optional<ReadOnlyObjectProperty<PixelApi>> selectedPixelAPI = Client.getClientFromURI(webServerURI)
                    .map(Client::getSelectedPixelApi);

            if (selectedPixelAPI.isPresent()) {
                setSupported(selectedPixelAPI.get());
                selectedPixelAPI.get().addListener(change -> setSupported(selectedPixelAPI.get()));
            } else {
                logger.warn(
                        "Could not find the web client corresponding to {}. Impossible to determine if this image ({}) is supported.",
                        webServerURI,
                        this
                );
            }
        }
    }

    private synchronized void setSupported(ReadOnlyObjectProperty<PixelApi> selectedPixelAPI) {
        isSupported.set(true);
        unsupportedReasons.clear();

        if (selectedPixelAPI == null) {
            isSupported.set(false);
            unsupportedReasons.add(UnsupportedReason.PIXEL_API_UNAVAILABLE);
        } else {
            if (!Optional.ofNullable(pixels)
                    .map(PixelInfo::imageType)
                    .map(ImageType::value)
                    .flatMap(ApisHandler::getPixelType)
                    .map(pixelType -> selectedPixelAPI.get().canReadImage(pixelType))
                    .orElse(false)
            ) {
                isSupported.set(false);
                unsupportedReasons.add(UnsupportedReason.PIXEL_TYPE);
            }

            if (!Optional.ofNullable(pixels)
                    .map(PixelInfo::numberOfChannels)
                    .map(c -> selectedPixelAPI.get().canReadImage(c))
                    .orElse(false)
            ) {
                isSupported.set(false);
                unsupportedReasons.add(UnsupportedReason.NUMBER_OF_CHANNELS);
            }
        }
    }
}
