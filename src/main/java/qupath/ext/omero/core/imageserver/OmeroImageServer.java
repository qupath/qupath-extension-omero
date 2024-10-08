package qupath.ext.omero.core.imageserver;

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
import java.util.stream.Stream;

/**
 * <p>{@link qupath.lib.images.servers.ImageServer Image server} of the extension.</p>
 * <p>Pixels are read using the {@link qupath.ext.omero.core.pixelapis PixelAPIs} package.</p>
 */
public class OmeroImageServer extends AbstractTileableImageServer implements PathObjectReader  {

    private static final Logger logger = LoggerFactory.getLogger(OmeroImageServer.class);
    private static final String PIXEL_API_ARGUMENT = "--pixelAPI";
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

        this.client.addOpenedImage(uri);
    }

    /**
     * <p>
     *     Attempt to create an OmeroImageServer.
     * </p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if a request failed for example).
     * </p>
     *
     * @param uri the image URI
     * @param client the corresponding WebClient
     * @param args optional arguments used to open the image
     * @return a CompletableFuture (that may complete exceptionally) with the OmeroImageServer
     */
    public static CompletableFuture<OmeroImageServer> create(URI uri, WebClient client, String... args) {
        OptionalLong id = WebUtilities.parseEntityId(uri);
        if (id.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(String.format(
                    "Impossible to parse an ID from the provided URI %s", uri
            )));
        }

        List<String> arguments = Arrays.stream(args).toList();

        return client.getApisHandler().getImageMetadata(id.getAsLong()).thenApply(metadata -> {
            Optional<PixelAPI> pixelAPIFromArgs = getPixelAPIFromArgs(client, arguments);

            PixelAPI pixelAPI;
            List<String> newArguments = List.of();
            if (pixelAPIFromArgs.isPresent()) {
                pixelAPI = pixelAPIFromArgs.get();
            } else {
                pixelAPI = client.getSelectedPixelAPI().get();
                if (pixelAPI == null) {
                    throw new IllegalStateException("No supplied pixel API and no selected pixel API");
                }

                newArguments = getPixelAPIArgs(pixelAPI);
            }
            String[] newArgs = Stream.concat(
                    arguments.stream(),
                    newArguments.stream()
            ).toArray(String[]::new);
            pixelAPI.setParametersFromArgs(newArgs);

            try {
                return new OmeroImageServer(
                        uri,
                        client,
                        pixelAPI.createReader(id.getAsLong(), metadata),
                        id.getAsLong(),
                        metadata,
                        newArgs
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
        return pixelAPIReader.readTile(tileRequest);
    }

    @Override
    public BufferedImage getDefaultThumbnail(int z, int t) throws IOException {
        if (isRGB()) {
            try {
                return client.getApisHandler().getThumbnail(
                        id,
                        Math.max(originalMetadata.getLevel(0).getWidth(), originalMetadata.getLevel(0).getHeight())
                ).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }
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
            return List.of();
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

    @Override
    public void close() throws Exception {
        pixelAPIReader.close();
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

    private static Optional<PixelAPI> getPixelAPIFromArgs(WebClient client, List<String> args) {
        String pixelAPIName = null;
        int i = 0;
        while (i < args.size()-1) {
            String parameter = args.get(i++);
            if (PIXEL_API_ARGUMENT.equalsIgnoreCase(parameter.trim())) {
                pixelAPIName = args.get(i++).trim();
            }
        }

        if (pixelAPIName != null) {
            for (PixelAPI pixelAPI: client.getAvailablePixelAPIs()) {
                if (pixelAPI.getName().equalsIgnoreCase(pixelAPIName)) {
                    return Optional.of(pixelAPI);
                }
            }
            logger.warn(String.format(
                    "The provided pixel API (%s) was not recognized, or the corresponding OMERO server doesn't support it. Another one will be used.",
                    pixelAPIName
            ));
        }

        return Optional.empty();
    }

    private static List<String> getPixelAPIArgs(PixelAPI pixelAPI) {
        return Stream.concat(
                Stream.of(PIXEL_API_ARGUMENT, pixelAPI.getName()),
                Arrays.stream(pixelAPI.getArgs())
        ).toList();
    }
}
