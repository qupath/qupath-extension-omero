package qupath.ext.omero.core.imageserver;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.shapes.Shape;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebUtilities;
import qupath.ext.omero.core.pixelapis.PixelAPI;
import qupath.ext.omero.core.pixelapis.PixelAPIReader;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectReader;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * <p>{@link qupath.lib.images.servers.ImageServer Image server} of the extension.</p>
 * <p>Pixels are read using the {@link qupath.ext.omero.core.pixelapis PixelAPIs} package.</p>
 */
public class OmeroImageServer extends AbstractTileableImageServer implements PathObjectReader  {

    private static final Logger logger = LoggerFactory.getLogger(OmeroImageServer.class);
    private static final String PIXEL_API_ARGUMENT = "--pixelAPI";
    private static final int METADATA_CACHE_SIZE = 50;
    private static final Cache<Long, CompletableFuture<Optional<ImageServerMetadata>>> metadataCache = CacheBuilder.newBuilder()
            .maximumSize(METADATA_CACHE_SIZE)
            .build();
    private final URI uri;
    private final WebClient client;
    private final PixelAPIReader pixelAPIReader;
    private final long id;
    private final ImageServerMetadata originalMetadata;
    private final String[] args;

    private OmeroImageServer(
            URI uri,
            WebClient client,
            PixelAPIReader pixelAPIReader,
            long id,
            ImageServerMetadata originalMetadata,
            String... args
    ) {
        this.uri = uri;
        this.client = client;
        this.pixelAPIReader = pixelAPIReader;
        this.id = id;
        this.originalMetadata = originalMetadata;
        this.args = args;
    }

    /**
     * Attempt to create an OmeroImageServer.
     * This should only be used by an {@link OmeroImageServerBuilder}.
     *
     * @param uri  the image URI
     * @param client  the corresponding WebClient
     * @param args  optional arguments used to open the image
     * @return an OmeroImageServer, or an empty Optional if an error occurred
     */
    static Optional<OmeroImageServer> create(URI uri, WebClient client, String... args) {
        OptionalLong id = WebUtilities.parseEntityId(uri);
        if (id.isPresent()) {
            Optional<ImageServerMetadata> originalMetadata = Optional.empty();
            try {
                originalMetadata = getOriginalMetadata(uri, client).get();
            } catch (Exception e) {
                logger.error("Error while retrieving metadata", e);
            }

            if (originalMetadata.isPresent()) {
                System.err.println(originalMetadata.get().getName());

                PixelAPI pixelAPI;
                var pixelAPIFromArgs = getPixelAPIFromArgs(client, args);

                if (pixelAPIFromArgs.isPresent()) {
                    pixelAPI = pixelAPIFromArgs.get();
                } else {
                    pixelAPI = client.getSelectedPixelAPI().get();
                    if (pixelAPI == null) {
                        logger.error("No selected pixel API");
                        return Optional.empty();
                    }

                    args = addPixelAPIToArgs(pixelAPI, args);
                }

                pixelAPI.setParametersFromArgs(args);

                if (pixelAPI.canReadImage(originalMetadata.get().getPixelType(), originalMetadata.get().getSizeC())) {
                    try {
                        client.addOpenedImage(uri);

                        PixelAPIReader apiReader = pixelAPI.createReader(id.getAsLong(), originalMetadata.get());

                        return Optional.of(new OmeroImageServer(
                                uri,
                                client,
                                apiReader,
                                id.getAsLong(),
                                originalMetadata.get(),
                                args
                        ));
                    } catch (IOException e) {
                        logger.error("Couldn't create pixel API reader", e);
                        return Optional.empty();
                    }
                } else {
                    logger.error("The selected pixel API (" + pixelAPI + ") can't read the provided image");
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        } else {
            logger.warn("Could not get image ID from " + uri);
            return Optional.empty();
        }
    }

    @Override
    protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
        return pixelAPIReader.readTile(tileRequest);
    }

    @Override
    public BufferedImage getDefaultThumbnail(int z, int t) throws IOException {
        Optional<BufferedImage> thumbnail = Optional.empty();

        if (isRGB()) {
            try {
                thumbnail = client.getApisHandler().getThumbnail(
                        id,
                        Math.max(originalMetadata.getLevel(0).getWidth(), originalMetadata.getLevel(0).getHeight())
                ).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }
        }

        if (thumbnail.isPresent()) {
            return thumbnail.get();
        } else {
            return super.getDefaultThumbnail(z, t);
        }
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return ImageServerBuilder.DefaultImageServerBuilder.createInstance(
                OmeroImageServerBuilder.class,
                getMetadata(),
                uri,
                args
        );
    }

    @Override
    protected String createID() {
        return getClass().getName() + ": " + uri.toString() + " args=" + Arrays.toString(args);
    }

    @Override
    public Collection<URI> getURIs() {
        return Collections.singletonList(uri);
    }

    @Override
    public String getServerType() {
        return String.format("OMERO (%s)", pixelAPIReader.getName());
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return originalMetadata;
    }

    @Override
    public Collection<PathObject> readPathObjects() {
        List<Shape> shapes;
        try {
            shapes = client.getApisHandler().getROIs(id).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error reading path objects", e);
            return Collections.emptyList();
        }

        Map<UUID, UUID> idToParentId = new HashMap<>();
        Map<UUID, PathObject> idToPathObject = new HashMap<>();
        for (Shape shape: shapes) {
            UUID id = shape.getQuPathId();
            idToPathObject.put(id, shape.createPathObject());
            idToParentId.put(id, shape.getQuPathParentId().orElse(null));
        }

        List<PathObject> pathObjects = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry: idToParentId.entrySet()) {
            if (idToPathObject.containsKey(entry.getValue())) {
                idToPathObject.get(entry.getValue()).addChildObject(idToPathObject.get(entry.getKey()));
            } else {
                pathObjects.add(idToPathObject.get(entry.getKey()));
            }
        }

        return pathObjects;
    }

    @Override
    public String toString() {
        return String.format("OMERO image server of %s", uri);
    }

    /**
     * Attempt to send some path objects to the OMERO server.
     *
     * @param pathObjects  the path objects to send
     * @param removeExistingAnnotations  whether to remove existing annotations of the image in the OMERO server
     * @return whether the operation succeeded
     */
    public boolean sendPathObjects(Collection<PathObject> pathObjects, boolean removeExistingAnnotations) {
        try {
            return client.getApisHandler().writeROIs(
                    id,
                    pathObjects.stream()
                            .map(Shape::createFromPathObject)
                            .flatMap(List::stream)
                            .toList(),
                    removeExistingAnnotations
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Could not send path objects");
            return false;
        }
    }

    /**
     * @return the client owning this image server
     */
    public WebClient getClient() {
        return client;
    }

    /**
     * @return the ID of the image
     */
    public long getId() {
        return id;
    }

    /**
     * <p>
     *     Attempt to retrieve metadata associated with the image specified by the
     *     provided parameters.
     * </p>
     * <p>
     *     The results are cached in a cache of size {@link #METADATA_CACHE_SIZE}.
     * </p>
     *
     * @param uri  the URI of the image
     * @param client  the client of the corresponding server
     * @return a CompletableFuture with the metadata, or an empty Optional if the request failed
     */
    public static CompletableFuture<Optional<ImageServerMetadata>> getOriginalMetadata(URI uri, WebClient client) {
        OptionalLong id = WebUtilities.parseEntityId(uri);

        if (id.isPresent()) {
            try {
                CompletableFuture<Optional<ImageServerMetadata>> request = metadataCache.get(
                        id.getAsLong(),
                        () -> client.getApisHandler().getImageMetadata(id.getAsLong()).thenApply(imageMetadataResponse -> {
                            if (imageMetadataResponse.isPresent()) {
                                ImageServerMetadata.Builder builder = new ImageServerMetadata.Builder(
                                        OmeroImageServer.class,
                                        uri.toString(),
                                        imageMetadataResponse.get().getSizeX(),
                                        imageMetadataResponse.get().getSizeY()
                                )
                                        .name(imageMetadataResponse.get().getImageName())
                                        .sizeT(imageMetadataResponse.get().getSizeT())
                                        .sizeZ(imageMetadataResponse.get().getSizeZ())
                                        .preferredTileSize(imageMetadataResponse.get().getTileSizeX(), imageMetadataResponse.get().getTileSizeY())
                                        .levels(imageMetadataResponse.get().getLevels())
                                        .pixelType(imageMetadataResponse.get().getPixelType())
                                        .channels(imageMetadataResponse.get().getChannels())
                                        .rgb(imageMetadataResponse.get().isRGB());

                                if (imageMetadataResponse.get().getMagnification().isPresent()) {
                                    builder.magnification(imageMetadataResponse.get().getMagnification().get());
                                }

                                if (imageMetadataResponse.get().getPixelWidthMicrons().isPresent() && imageMetadataResponse.get().getPixelHeightMicrons().isPresent()) {
                                    builder.pixelSizeMicrons(
                                            imageMetadataResponse.get().getPixelWidthMicrons().get(),
                                            imageMetadataResponse.get().getPixelHeightMicrons().get()
                                    );
                                }

                                if (imageMetadataResponse.get().getZSpacingMicrons().isPresent() && imageMetadataResponse.get().getZSpacingMicrons().get() > 0) {
                                    builder.zSpacingMicrons(imageMetadataResponse.get().getZSpacingMicrons().get());
                                }

                                return Optional.of(builder.build());
                            } else {
                                return Optional.empty();
                            }
                        })
                );

                request.thenAccept(response -> {
                    if (response.isEmpty()) {
                        metadataCache.invalidate(id.getAsLong());
                    }
                });
                return request;
            } catch (ExecutionException e) {
                logger.error("Error when retrieving metadata", e);
                return CompletableFuture.completedFuture(Optional.empty());
            }
        } else {
            logger.warn("Could not get image ID from " + uri);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static Optional<PixelAPI> getPixelAPIFromArgs(WebClient client, String... args) {
        String pixelAPIName = null;
        int i = 0;
        while (i < args.length-1) {
            String parameter = args[i++];
            if (PIXEL_API_ARGUMENT.equalsIgnoreCase(parameter.trim())) {
                pixelAPIName = args[i++].trim();
            }
        }

        if (pixelAPIName != null) {
            for (PixelAPI pixelAPI: client.getAvailablePixelAPIs()) {
                if (pixelAPI.getName().equalsIgnoreCase(pixelAPIName)) {
                    return Optional.of(pixelAPI);
                }
            }
            logger.warn(
                    "The provided pixel API (" + pixelAPIName + ") was not recognized, or the corresponding OMERO server doesn't support it." +
                            "Another one will be used."
            );
        }

        return Optional.empty();
    }

    private static String[] addPixelAPIToArgs(PixelAPI pixelAPI, String[] args) {
        String[] pixelApiArgs = pixelAPI.getArgs();
        int currentArgsSize = args.length;

        args = Arrays.copyOf(args, args.length + 2 + pixelApiArgs.length);

        args[currentArgsSize] = PIXEL_API_ARGUMENT;
        args[currentArgsSize + 1] = pixelAPI.getName();

        for (int i=0; i<pixelApiArgs.length; ++i) {
            args[currentArgsSize + 2 + i] = pixelApiArgs[i];
        }

        return args;
    }
}
