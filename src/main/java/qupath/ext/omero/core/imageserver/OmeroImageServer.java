package qupath.ext.omero.core.imageserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;
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
import java.lang.ref.Cleaner;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * {@link qupath.lib.images.servers.ImageServer Image server} of the extension.
 * <p>
 * Pixels are read using the {@link qupath.ext.omero.core.pixelapis PixelAPIs} package.
 */
public class OmeroImageServer extends AbstractTileableImageServer implements PathObjectReader  {

    private static final Logger logger = LoggerFactory.getLogger(OmeroImageServer.class);
    private static final Cleaner cleaner = Cleaner.create();
    private final URI imageUri;
    private final Client client;
    private final long id;
    private final ImageServerMetadata originalMetadata;
    private final PixelApiReader pixelAPIReader;
    private final String apiName;
    private final List<String> args;
    private final Cleaner.Cleanable cleanable;
    private record OmeroImageServerState(PixelApiReader pixelApiReader) implements Runnable {
        public void run() {
            try {
                pixelApiReader.close();
            } catch (Exception e) {
                logger.error("Error when closing pixel API reader", e);
            }
        }
    }

    /**
     * Create an OmeroImageServer. This may take a few seconds as it will send a request to retrieve
     * the image metadata.
     *
     * @param imageUri a link to the image to open
     * @param client the client that will be used to get image information
     * @param pixelApi the pixel API to use when reading the image
     * @param args a list of arguments specifying how to open the image with the provided pixel API. They are specified in
     *             {@link qupath.ext.omero.core.pixelapis}
     * @throws ExecutionException if an error occurred while retrieving the image metadata or creating the reader
     * @throws InterruptedException if retrieving the image metadata or creating the reader was interrupted
     * @throws IllegalArgumentException if the image ID cannot be parsed from the provided URI or if the image cannot be read
     */
    public OmeroImageServer(URI imageUri, Client client, PixelApi pixelApi, List<String> args) throws ExecutionException, InterruptedException, IOException {
        logger.debug("Creating OMERO image server to open {} with {} and {}, and with args {}", imageUri, client, pixelApi, args);

        this.imageUri = imageUri;
        this.client = client;
        this.id = ApisHandler.parseEntity(imageUri).map(ServerEntity::getId).orElseThrow(() -> new IllegalArgumentException(String.format(
                "Impossible to parse an ID from the provided URI %s", imageUri
        )));
        ImageServerMetadata metadata = client.getApisHandler().getImageMetadata(id).get();
        this.pixelAPIReader = pixelApi.createReader(
                id,
                metadata,
                args
        );
        this.originalMetadata = this.pixelAPIReader.updateMetadata(metadata);
        logger.debug("Metadata updated from {} to {}", metadata, this.originalMetadata);

        this.apiName = pixelApi.getName();
        this.args = args;
        this.cleanable = cleaner.register(this, new OmeroImageServerState(pixelAPIReader));

        this.client.addOpenedImage(imageUri);

        logger.debug("OMERO image server to open {} created", imageUri);
    }

    @Override
    protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
        return pixelAPIReader.readTile(tileRequest);
    }

    @Override
    public BufferedImage getDefaultThumbnail(int z, int t) throws IOException {
        if (isRGB()) {
            logger.debug("Requesting thumbnail while the image is RGB. Trying to use web API");

            try {
                return client.getApisHandler().getThumbnail(
                        id,
                        Math.max(getMetadata().getLevel(0).getWidth(), getMetadata().getLevel(0).getHeight())
                ).get();
            } catch (Exception e) {
                logger.debug("Cannot get thumbnail through web API. Using default way of retrieving thumbnail instead", e);

                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return super.getDefaultThumbnail(z, t);
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
     * Get all path objects of this image stored on the OMERO server.
     * This function may take a while as it sends an HTTP request.
     *
     * @return the list of objects of this image stored on the OMERO server
     */
    @Override
    public Collection<PathObject> readPathObjects() {
        logger.debug("Reading all path objects stored on the OMERO server at {}", imageUri);

        try {
            return Shape.createPathObjects(client.getApisHandler().getShapes(id, -1).get());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error reading path objects", e);
            return List.of();
        }
    }

    @Override
    public void close() throws Exception {
        cleanable.clean();
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
}
