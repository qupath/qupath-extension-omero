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
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.bioformats.OMEPixelParser;
import qupath.ext.omero.core.pixelapis.PixelAPIReader;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Read pixel values using the <a href="https://omero.readthedocs.io/en/v5.6.7/developers/Java.html">OMERO gateway</a>.
 */
class IceReader implements PixelAPIReader {

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
     * @param gatewayWrapper  a wrapper around a valid connection with an ICE server
     * @param imageID  the ID of the image to open
     * @param channels  the channels of the image to open
     * @throws IOException  when the reader creation fails
     */
    public IceReader(GatewayWrapper gatewayWrapper, long imageID, List<ImageChannel> channels) throws IOException {
        try {
            if (gatewayWrapper.isConnected()) {
                var imageAndContext = getImageAndContext(gatewayWrapper.getGateway(), imageID, gatewayWrapper.getGateway().getLoggedInUser().getGroupId());
                if (imageAndContext.isPresent()) {
                    context = imageAndContext.get().context;
                    PixelsData pixelsData = imageAndContext.get().image.getDefaultPixels();

                    readerPool = new ObjectPool<>(
                            NUMBER_OF_READERS,
                            () -> {
                                try {
                                    RawPixelsStorePrx reader = gatewayWrapper.getGateway().getPixelsStore(context);
                                    reader.setPixelsId(pixelsData.getId(), false);
                                    return reader;
                                } catch (DSOutOfServiceException | ServerError e) {
                                    logger.error("Error when creating RawPixelsStorePrx", e);
                                    return null;
                                }
                            },
                            reader -> {
                                try {
                                    reader.close();
                                } catch (ServerError e) {
                                    logger.error("Error when closing reader", e);
                                }
                            }
                    );

                    try {
                        var reader = readerPool.get();
                        if (reader.isPresent()) {
                            numberOfResolutionLevels = reader.get().getResolutionLevels();
                            readerPool.giveBack(reader.get());
                        } else {
                            throw new IOException("Cannot create RawPixelsStorePrx");
                        }
                    } catch (InterruptedException e) {
                        throw new IOException(e);
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
                } else {
                    throw new IOException("Couldn't find requested image of ID " + imageID);
                }
            } else {
                throw new IOException("Could not connect to the ICE server");
            }
        } catch (DSOutOfServiceException | ExecutionException | ServerError e) {
            throw new IOException(e);
        }
    }

    @Override
    public BufferedImage readTile(TileRequest tileRequest) throws IOException {
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
                throw new IOException("Cannot create RawPixelsStorePrx");
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
    public String getName() {
        return IceAPI.NAME;
    }

    @Override
    public void close() throws Exception {
        readerPool.close();
    }

    @Override
    public String toString() {
        return String.format("Ice reader for %s", context.getServerInformation());
    }

    private static Optional<ImageContextWrapper> getImageAndContext(Gateway gateway, long imageID, long userGroupId) throws ExecutionException, DSOutOfServiceException, ServerError {
        SecurityContext context = new SecurityContext(userGroupId);
        BrowseFacility browser = gateway.getFacility(BrowseFacility.class);
        try {
            return Optional.of(new ImageContextWrapper(
                    browser.getImage(context, imageID),
                    context
            ));
        } catch (Exception ignored) {}

        List<ExperimenterGroup> groups = gateway.getAdminService(context).containedGroups(gateway.getLoggedInUser().asExperimenter().getId().getValue());
        for (ExperimenterGroup group: groups) {
            context = new SecurityContext(group.getId().getValue());

            try {
                return Optional.of(new ImageContextWrapper(
                        browser.getImage(context, imageID),
                        context
                ));
            } catch (Exception ignored) {}
        }
        return Optional.empty();
    }
}
