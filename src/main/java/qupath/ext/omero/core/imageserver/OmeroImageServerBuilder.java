package qupath.ext.omero.core.imageserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.WebUtilities;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * <p>{@link qupath.lib.images.servers.ImageServerBuilder Image server builder} of this extension.</p>
 * <p>It creates an {@link OmeroImageServer}.</p>
 */
public class OmeroImageServerBuilder implements ImageServerBuilder<BufferedImage> {

    private static final Logger logger = LoggerFactory.getLogger(OmeroImageServerBuilder.class);
    private static final float SUPPORT_LEVEL = 4;

    @Override
    public ImageServer<BufferedImage> buildServer(URI uri, String... args) {
        try {
            WebClient client = getClientAndCheckURIReachable(uri, args);

            if (client == null) {
                return null;
            } else {
                return OmeroImageServer.create(uri, client, args).get();
            }
        } catch (Exception e) {
            logger.debug("Error while creating OMERO image server", e);
            return null;
        }
    }

    @Override
    public ImageServerBuilder.UriImageSupport<BufferedImage> checkImageSupport(URI entityURI, String... args) {
        try {
            WebClient client = getClientAndCheckURIReachable(entityURI, args);

            List<ImageServerBuilder.ServerBuilder<BufferedImage>> builders = WebUtilities.getImagesURIFromEntityURI(
                            entityURI,
                            client.getApisHandler()
                    )
                    .join()
                    .stream()
                    .map(uri -> createServerBuilder(client, uri, args))
                    .map(CompletableFuture::join)
                    .toList();

            return UriImageSupport.createInstance(
                    this.getClass(),
                    builders.isEmpty() ? 0 : SUPPORT_LEVEL,
                    builders
            );
        } catch (Exception e) {
            logger.debug("Error when checking image support", e);

            return UriImageSupport.createInstance(
                    this.getClass(),
                    0,
                    List.of()
            );
        }
    }

    @Override
    public String getName() {
        return "OMERO";
    }

    @Override
    public String getDescription() {
        return "Image server using OMERO";
    }

    @Override
    public Class<BufferedImage> getImageType() {
        return BufferedImage.class;
    }

    @Override
    public boolean matchClassName(String... classNames) {
        for (var className : classNames) {
            if (this.getClass().getName().equals(className) ||
                    this.getClass().getSimpleName().equals(className) ||
                    OmeroImageServer.class.getName().equals(className) ||
                    OmeroImageServer.class.getSimpleName().equals(className) ||
                    "omero-web".equalsIgnoreCase(className))
                return true;
        }
        return false;
    }

    private static WebClient getClientAndCheckURIReachable(URI uri, String... args) throws Exception {
        int statusCode = RequestSender.getStatusCodeOfGetRequest(uri, false).get();

        return WebClients.createClientSync(
                uri.toString(),
                statusCode == 200 ? WebClient.Authentication.TRY_TO_SKIP : WebClient.Authentication.ENFORCE,
                args
        );
    }

    private static CompletableFuture<ImageServerBuilder.ServerBuilder<BufferedImage>> createServerBuilder(
            WebClient client,
            URI uri,
            String... args
    ) {
        return client.getApisHandler()
                .getImageMetadata(WebUtilities
                        .parseEntityId(uri)
                        .orElseThrow(() -> new IllegalArgumentException(String.format(
                                "ID not found in %s", uri
                        )))
                )
                .thenApply(metadata -> DefaultImageServerBuilder.createInstance(
                        OmeroImageServerBuilder.class,
                        metadata,
                        uri,
                        args
                ));
    }
}
