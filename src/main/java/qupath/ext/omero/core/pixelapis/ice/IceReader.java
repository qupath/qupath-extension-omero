package qupath.ext.omero.core.pixelapis.ice;

import omero.ServerError;
import omero.api.RawPixelsStorePrx;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.model.ExperimenterGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.ObjectPool;
import qupath.ext.omero.core.pixelapis.PixelApiReader;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ImageChannel;
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
    private static final int NUMBER_OF_READERS = Runtime.getRuntime().availableProcessors() + 5;    // Tasks that include I/O or other blocking operations
                                                                                                    // should use a bit more than the number of cores
    private final ObjectPool<RawPixelsStorePrx> readerPool;
    private final int numberOfResolutionLevels;
    private final int nChannels;
    private final int effectiveNChannels;
    private final PixelType pixelType;
    private final ColorModel colorModel;
    private final SecurityContext context;
    private record ImageContextWrapper(ImageData image, SecurityContext context) {}

    /**
     * Creates a new Ice reader.
     *
     * @param gatewayWrapper a wrapper around a valid connection with an ICE server
     * @param imageID the ID of the image to open
     * @param channels the channels of the image to open
     * @throws Exception when the reader creation fails
     */
    public IceReader(GatewayWrapper gatewayWrapper, long imageID, List<ImageChannel> channels) throws Exception {
        logger.debug("Creating ICE reader for image of ID {}...", imageID);

        ImageContextWrapper imageAndContext = getImageAndContext(
                gatewayWrapper.getGateway(),
                imageID,
                gatewayWrapper.getGateway().getLoggedInUser().getGroupId()
        );
        context = imageAndContext.context;
        PixelsData pixelsData = imageAndContext.image.getDefaultPixels();

        readerPool = new ObjectPool<>(
                NUMBER_OF_READERS,
                () -> {
                    try {
                        RawPixelsStorePrx reader = gatewayWrapper.getGateway().getPixelsStore(context);
                        reader.setPixelsId(pixelsData.getId(), false);

                        logger.debug("RawPixelsStorePrx for image with ID {} created", imageID);
                        return reader;
                    } catch (DSOutOfServiceException | ServerError e) {
                        logger.error("Error when creating RawPixelsStorePrx", e);
                        return null;
                    }
                },
                reader -> {
                    try {
                        reader.close();
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

        return new OMEPixelParser.Builder()
                .isInterleaved(false)
                .pixelType(pixelType)
                .byteOrder(ByteOrder.BIG_ENDIAN)
                .normalizeFloats(false)
                .effectiveNChannels(effectiveNChannels)
                .build()
                .parse(bytes, tileRequest.getTileWidth(), tileRequest.getTileHeight(), nChannels, colorModel);
    }

    @Override
    public void close() throws Exception {
        readerPool.close();
    }

    @Override
    public String toString() {
        return String.format("Ice reader for %s", context.getServerInformation().getHost());
    }

    private static ImageContextWrapper getImageAndContext(Gateway gateway, long imageID, long userGroupId) throws Exception {
        SecurityContext context = new SecurityContext(userGroupId);
        BrowseFacility browser = gateway.getFacility(BrowseFacility.class);
        try {
            return new ImageContextWrapper(
                    browser.getImage(context, imageID),
                    context
            );
        } catch (Exception e) {
            logger.debug("Cannot get image {} from group {}. Trying other groups of the current user...", imageID, userGroupId, e);
        }

        List<ExperimenterGroup> groups = gateway.getAdminService(context).containedGroups(gateway.getLoggedInUser().asExperimenter().getId().getValue());
        for (int i=0; i<groups.size(); i++) {
            context = new SecurityContext(groups.get(i).getId().getValue());

            try {
                return new ImageContextWrapper(
                        browser.getImage(context, imageID),
                        context
                );
            } catch (Exception e) {
                if (i < groups.size()-1) {
                    logger.debug(
                            "Cannot get image {} from group {}. Trying group {}...",
                            imageID,
                            groups.get(i).getId().getValue(),
                            groups.get(i+1).getId().getValue(),
                            e
                    );
                } else {
                    logger.error(
                            "Cannot get image {} from group {}. No more groups available",
                            imageID,
                            groups.get(i).getId().getValue(),
                            e
                    );
                    throw e;
                }
            }
        }

        throw new IllegalStateException(String.format("Cannot get image %d. No groups available", imageID));
    }
}
