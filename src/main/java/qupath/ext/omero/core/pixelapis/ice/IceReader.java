package qupath.ext.omero.core.pixelapis.ice;

import omero.ServerError;
import omero.api.RawPixelsStorePrx;
import omero.api.ResolutionDescription;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.ObjectPool;
import qupath.ext.omero.core.pixelapis.PixelApiReader;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.bioformats.OMEPixelParser;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;

/**
 * Read pixel values using the <a href="https://omero.readthedocs.io/en/v5.6.7/developers/Java.html">OMERO gateway</a>.
 * <p>
 * This reader must be {@link #close() closed} once no longer used.
 */
class IceReader implements PixelApiReader {

    private static final Logger logger = LoggerFactory.getLogger(IceReader.class);
    private final long groupId;
    private final boolean isRgb;
    private final SecurityContext context;
    private final ImageData imageData;
    private final ObjectPool<RawPixelsStorePrx> readerPool;
    private final int numberOfResolutionLevels;
    private final int nChannels;
    private final int effectiveNChannels;
    private final PixelType pixelType;
    private final ColorModel colorModel;

    /**
     * Creates a new Ice reader.
     *
     * @param gatewayWrapper a wrapper around a valid connection with an ICE server
     * @param imageId the ID of the image to open
     * @param groupId the ID of the group owning the image to open
     * @param channels the channels of the image to open
     * @param isRgb whether the returned tiles should have the RGB or ARGB format
     * @param numberOfReaders the maximum number of threads to use when reading tiles
     * @throws Exception when the reader creation fails
     */
    public IceReader(GatewayWrapper gatewayWrapper, long imageId, long groupId, List<ImageChannel> channels, boolean isRgb, int numberOfReaders) throws Exception {
        logger.debug("Creating ICE reader for image of ID {} with group of ID {}...", imageId, groupId);

        this.groupId = groupId;
        this.isRgb = isRgb;

        context = new SecurityContext(groupId);
        BrowseFacility browser = gatewayWrapper.getGateway().getFacility(BrowseFacility.class);
        imageData = browser.getImage(context, imageId);
        PixelsData pixelsData = imageData.getDefaultPixels();

        readerPool = new ObjectPool<>(
                numberOfReaders,
                () -> {
                    try {
                        RawPixelsStorePrx reader = gatewayWrapper.getGateway().getPixelsStore(context);
                        reader.setPixelsId(pixelsData.getId(), false);

                        logger.debug("RawPixelsStorePrx for image with ID {} created", imageId);
                        return reader;
                    } catch (DSOutOfServiceException | ServerError e) {
                        logger.error("Error when creating RawPixelsStorePrx", e);
                        return null;
                    }
                },
                reader -> {
                    try {
                        reader.close();

                        logger.debug("RawPixelsStorePrx for image with ID {} closed", imageId);
                    } catch (Exception e) {
                        logger.warn("Error when closing RawPixelsStorePrx", e);
                    }
                }
        );

        var reader = readerPool.get();
        if (reader.isPresent()) {
            numberOfResolutionLevels = reader.get().getResolutionLevels();
            readerPool.giveBack(reader.get());
        } else {
            throw new IllegalStateException("Cannot create RawPixelsStorePrx. Impossible to get the number of resolution levels");
        }

        nChannels = channels.size();
        effectiveNChannels = pixelsData.getSizeC();
        pixelType = switch (pixelsData.getPixelType()) {
            case PixelsData.INT8_TYPE -> PixelType.INT8;
            case PixelsData.UINT8_TYPE -> PixelType.UINT8;
            case PixelsData.INT16_TYPE -> PixelType.INT16;
            case PixelsData.UINT16_TYPE -> PixelType.UINT16;
            case PixelsData.UINT32_TYPE -> PixelType.UINT32;
            case PixelsData.INT32_TYPE -> PixelType.INT32;
            case PixelsData.FLOAT_TYPE -> PixelType.FLOAT32;
            case PixelsData.DOUBLE_TYPE -> PixelType.FLOAT64;
            default -> throw new IllegalArgumentException("Unsupported pixel type " + pixelsData.getPixelType());
        };
        colorModel = ColorModelFactory.createColorModel(pixelType, channels);

        logger.debug("Created ICE reader for image with ID {}", imageId);
    }

    @Override
    public BufferedImage readTile(TileRequest tileRequest) throws IOException {
        logger.debug("Reading tile {} from ICE", tileRequest);

        byte[][] bytes = new byte[effectiveNChannels][];

        Optional<RawPixelsStorePrx> reader = Optional.empty();
        try {
            reader = readerPool.get();
            if (reader.isPresent()) {
                reader.get().setResolutionLevel(numberOfResolutionLevels - 1 - tileRequest.getLevel());

                for (int channel = 0; channel < effectiveNChannels; channel++) {
                    bytes[channel] = reader.get().getTile(
                            tileRequest.getZ(),
                            channel,
                            tileRequest.getT(),
                            tileRequest.getTileX(),
                            tileRequest.getTileY(),
                            tileRequest.getTileWidth(),
                            tileRequest.getTileHeight()
                    );
                }
            } else {
                throw new IOException(String.format("Cannot create RawPixelsStorePrx. Tile %s won't be read", tileRequest));
            }
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            reader.ifPresent(readerPool::giveBack);
        }


        if (isRgb && (effectiveNChannels == 3 || effectiveNChannels == 4)) {
            int[] rgbArray = new int[bytes[0].length];

            if (effectiveNChannels == 3) {
                for (int i=0; i<rgbArray.length; i++) {
                    rgbArray[i] = ColorTools.packRGB(bytes[0][i], bytes[1][i], bytes[2][i]);
                }
            } else {
                for (int i=0; i<rgbArray.length; i++) {
                    rgbArray[i] = ColorTools.packARGB(bytes[0][i], bytes[1][i], bytes[2][i], bytes[3][i]);
                }
            }

            BufferedImage image = new BufferedImage(
                    tileRequest.getTileWidth(),
                    tileRequest.getTileHeight(),
                    effectiveNChannels == 3 ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB
            );
            image.setRGB(0, 0, image.getWidth(), image.getHeight(), rgbArray, 0, image.getWidth());
            return image;
        } else {
            return new OMEPixelParser.Builder()
                    .isInterleaved(false)
                    .pixelType(pixelType)
                    .byteOrder(ByteOrder.BIG_ENDIAN)
                    .normalizeFloats(false)
                    .effectiveNChannels(effectiveNChannels)
                    .build()
                    .parse(bytes, tileRequest.getTileWidth(), tileRequest.getTileHeight(), nChannels, colorModel);
        }
    }

    @Override
    public ImageServerMetadata updateMetadata(ImageServerMetadata originalMetadata) {
        logger.debug("Updating metadata from ICE reader");

        return vsiResolutionSizeFix(originalMetadata);
    }

    @Override
    public void close() throws Exception {
        logger.debug("Closing ICE reader of image with ID {}", imageData.getId());

        readerPool.close();
    }

    @Override
    public String toString() {
        return String.format("Ice reader for image with ID %d", imageData.getId());
    }

    /**
     * @return the ID of the group owning the image opened by this reader
     */
    public long getGroupId() {
        return groupId;
    }

    /**
     * In some VSI images, the calculated downsamples for width & height can be wildly discordant
     * (see <a href="https://forum.image.sc/t/qupath-omero-weird-pyramid-levels/65484">this issue</a>).
     * This function fixes that by computing new downsamples.
     *
     * @param originalMetadata the original metadata of the image
     * @return a modified (or not) version of the provided metadata with the VSI fix
     */
    private ImageServerMetadata vsiResolutionSizeFix(ImageServerMetadata originalMetadata) {
        if (!imageData.getFormat().equals("CellSens")) {
            logger.debug("Image format {} different from CellSens. No VSI resolution fix needed", imageData.getFormat());
            return originalMetadata;
        }

        try {
            RawPixelsStorePrx reader = readerPool.get().orElseThrow();

            var resolutionBuilder = new ImageServerMetadata.ImageResolutionLevel.Builder(originalMetadata.getWidth(), originalMetadata.getHeight());
            ResolutionDescription[] levelDescriptions = reader.getResolutionDescriptions();

            for (int i=0; i<levelDescriptions.length; i++) {
                double downsampleX = (double) originalMetadata.getWidth() / levelDescriptions[i].sizeX;
                double downsampleY = (double) originalMetadata.getHeight() / levelDescriptions[i].sizeY;
                double downsample = Math.pow(2, i);

                if (GeneralTools.almostTheSame(downsampleX, downsampleY, 0.01)) {
                    resolutionBuilder.addLevel(levelDescriptions[i].sizeX, levelDescriptions[i].sizeY);
                } else {
                    logger.warn("Non-matching downsamples calculated for level {} ({} and {}); will use {} instead", i, downsampleX, downsampleY, downsample);
                    resolutionBuilder.addLevel(downsample, levelDescriptions[i].sizeX, levelDescriptions[i].sizeY);
                }
            }

            List<ImageServerMetadata.ImageResolutionLevel> levels = resolutionBuilder.build();
            logger.debug("VSI resolution fix: original metadata levels updated from {} to {}", originalMetadata.getLevels(), levels);

            return new ImageServerMetadata.Builder(originalMetadata)
                    .levels(levels)
                    .build();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            logger.debug("Cannot apply VSI resolution fix. Returning original metadata", e);
            return originalMetadata;
        }
    }
}
