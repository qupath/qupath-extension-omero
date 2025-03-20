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
    @SerializedName(value = "url:datasets") private String datasetsUrl;
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
            case 5 -> getImageDimensions().map(d -> d[0] + " px").orElse("-");
            case 6 -> getImageDimensions().map(d -> d[1] + " px").orElse("-");
            case 7 -> {
                var dimensions = getImageDimensions();
                var pixelType = getPixelType();

                if (dimensions.isPresent() && pixelType.isPresent()) {
                    var quPathPixelType = ApisHandler.getPixelType(pixelType.get());

                    if (quPathPixelType.isPresent()) {
                        int width = dimensions.get()[0];
                        int height = dimensions.get()[1];
                        int bytesPerPixel = quPathPixelType.get().getBytesPerPixel();
                        int nChannels = dimensions.get()[2];
                        int zSlices = dimensions.get()[3];
                        int timePoints = dimensions.get()[4];

                        double size = width / 1024.0 * height / 1024.0 *
                                bytesPerPixel * nChannels *
                                zSlices * timePoints;
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
            case 8 -> getImageDimensions().map(d -> String.valueOf(d[2])).orElse("-");
            case 9 -> getImageDimensions().map(d -> String.valueOf(d[3])).orElse("-");
            case 10 -> getImageDimensions().map(d -> String.valueOf(d[4])).orElse("-");
            case 11 -> getPhysicalSizeX().map(x -> x.getValue() + " " + x.getSymbol().orElse("")).orElse("-");
            case 12 -> getPhysicalSizeY().map(x -> x.getValue() + " " + x.getSymbol().orElse("")).orElse("-");
            case 13 -> getPhysicalSizeZ().map(x -> x.getValue() + " " + x.getSymbol().orElse("")).orElse("-");
            case 14 -> getPixelType().orElse("-");
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

    /**
     * @return a link to the datasets owning this image, or an empty Optional if it doesn't exist
     */
    public Optional<String> getDatasetsUrl() {
        return Optional.ofNullable(datasetsUrl);
    }

    private Optional<int[]> getImageDimensions() {
        if (pixels == null) {
            return Optional.empty();
        } else {
            return Optional.of(pixels.getImageDimensions());
        }
    }

    private Optional<String> getPixelType() {
        return pixels == null ? Optional.empty() : pixels.getPixelType();
    }

    private Optional<PhysicalSize> getPhysicalSizeX() {
        return pixels == null ? Optional.empty() : pixels.getPhysicalSizeX();
    }

    private Optional<PhysicalSize> getPhysicalSizeY() {
        return pixels == null ? Optional.empty() : pixels.getPhysicalSizeY();
    }

    private Optional<PhysicalSize> getPhysicalSizeZ() {
        return pixels == null ? Optional.empty() : pixels.getPhysicalSizeZ();
    }

    private synchronized void setUpSupported() {
        if (isSupported == null) {
            if (webServerURI == null) {
                throw new IllegalStateException(
                        "The web server URI has not been set on this image. See the setWebServerURI(URI) function."
                );
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
            if (!getPixelType()
                    .flatMap(ApisHandler::getPixelType)
                    .map(pixelType -> selectedPixelAPI.get().canReadImage(pixelType))
                    .orElse(false)
            ) {
                isSupported.set(false);
                unsupportedReasons.add(UnsupportedReason.PIXEL_TYPE);
            }

            if (!getImageDimensions()
                    .map(imageDimensions -> selectedPixelAPI.get().canReadImage(imageDimensions[3]))
                    .orElse(false)
            ) {
                isSupported.set(false);
                unsupportedReasons.add(UnsupportedReason.NUMBER_OF_CHANNELS);
            }
        }
    }
}
