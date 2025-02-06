package qupath.ext.omero.core.imageserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.entities.shapes.Shape;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.core.pixelapis.PixelApiReader;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * {@link qupath.lib.images.servers.ImageServer Image server} of the extension.
 * <p>
 * Pixels are read using the {@link qupath.ext.omero.core.pixelapis PixelAPIs} package.
 */
public class OmeroImageServer extends AbstractTileableImageServer implements PathObjectReader  {

    private static final Logger logger = LoggerFactory.getLogger(OmeroImageServer.class);
    private static final String PIXEL_API_ARGUMENT = "--pixelAPI";
    private final URI imageUri;
    private final Client client;
    private final long id;
    private final ImageServerMetadata originalMetadata;
    private final PixelApiReader pixelAPIReader;
    private final String apiName;
    private final List<String> args;

    /**
     * Create an OmeroImageServer. This may take a few seconds as it will send a request to retrieve
     * the image metadata.
     *
     * @param imageUri a link to the image to open
     * @param client the client that will be used to get image information
     * @param args a list of arguments specifying how to open the image: {@link #PIXEL_API_ARGUMENT} to define the
     *             pixel API to use, and additional arguments specified in the chosen pixel API of
     *             {@link qupath.ext.omero.core.pixelapis}.
     * @throws ExecutionException if an error occurred while retrieving the image metadata
     * @throws InterruptedException if retrieving the image metadata was interrupted
     * @throws IOException if a {@link PixelApiReader} cannot be created
     * @throws IllegalStateException if no pixel API was found in the arguments and the client doesn't currently have a
     * selected pixel API (see {@link Client#getSelectedPixelAPI()})
     * @throws IllegalArgumentException if the image ID cannot be parsed from the provided URI or if the image cannot be read
     */
    public OmeroImageServer(URI imageUri, Client client, List<String> args) throws ExecutionException, InterruptedException, IOException {
        PixelApi pixelApi = getPixelAPIFromArgs(client, args).orElse(client.getSelectedPixelAPI().get());
        if (pixelApi == null) {
            throw new IllegalStateException("No supplied pixel API and no selected pixel API");
        }

        this.imageUri = imageUri;
        this.client = client;
        this.id = ApisHandler.parseEntityId(imageUri).orElseThrow(() -> new IllegalArgumentException(String.format(
                "Impossible to parse an ID from the provided URI %s", imageUri
        )));
        this.originalMetadata = client.getApisHandler().getImageMetadata(id).get();
        this.pixelAPIReader = pixelApi.createReader(
                id,
                originalMetadata,
                IntStream.range(0, args.size() / 2)
                        .boxed()
                        .collect(Collectors.toMap(
                                i -> args.get(i * 2),
                                i -> args.get(i * 2 + 1),
                                (a, b) -> b,
                                HashMap::new
                        ))
        );//TODO: lazy initialize ?
        this.apiName = pixelApi.getName();
        this.args = ArgsUtils.replaceArgs(args, pixelApi.getArgs());

        this.client.addOpenedImage(imageUri);
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
                imageUri,
                args.toArray(new String[0])
        );
    }

    @Override
    protected String createID() {
        return getClass().getName() + ": " + imageUri.toString() + " args=" + args;
    }

    @Override
    public Collection<URI> getURIs() {
        return Collections.singletonList(imageUri);
    }

    @Override
    public String getServerType() {
        return String.format("OMERO (%s)", apiName);
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return originalMetadata;
    }

    /**
     * Get the path objects stored of this image stored on the OMERO server.
     * This function may take a while as it sends an HTTP request.
     *
     * @return the list of objects of this image stored on the OMERO server
     */
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
        return String.format("OMERO image server of %s", imageUri);
    }

    /**
     * @return the client owning this image server
     */
    public Client getClient() {
        return client;
    }

    /**
     * @return the ID of the image
     */
    public long getId() {
        return id;
    }

    private static Optional<PixelApi> getPixelAPIFromArgs(Client client, List<String> args) {
        String pixelAPIName = null;
        int i = 0;
        while (i < args.size()-1) {
            String parameter = args.get(i++);
            if (PIXEL_API_ARGUMENT.equalsIgnoreCase(parameter.trim())) {
                pixelAPIName = args.get(i++).trim();
            }
        }

        if (pixelAPIName != null) {
            for (PixelApi pixelAPI: client.getAllPixelApis()) {
                if (pixelAPI.getName().equalsIgnoreCase(pixelAPIName)) {
                    if (!pixelAPI.isAvailable().get()) {
                        logger.warn(
                                "The provided pixel API ({}) was found but is not available at the moment. This may cause issue when using it",
                                pixelAPIName
                        );
                    }
                    return Optional.of(pixelAPI);
                }
            }
            logger.warn(
                    "The provided pixel API ({}) was not recognized among the pixel APIs of this client ({}). Another one will be used.",
                    pixelAPIName,
                    client.getAllPixelApis()
            );
        }

        return Optional.empty();
    }
}
