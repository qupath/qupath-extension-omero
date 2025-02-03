package qupath.ext.omero.core.imageserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.gui.login.LoginForm;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.ext.omero.core.Utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@link qupath.lib.images.servers.ImageServerBuilder Image server builder} of the OMERO extension.
 * <p>
 * It creates an {@link OmeroImageServer}.
 */
public class OmeroImageServerBuilder implements ImageServerBuilder<BufferedImage> {

    private static final Logger logger = LoggerFactory.getLogger(OmeroImageServerBuilder.class);
    private static final String USERTYPE_ARG = "--usertype";
    private static final String USERNAME_ARG = "--username";
    private static final float SUPPORT_LEVEL = 4;

    /**
     * Attempt to create a {@link OmeroImageServer} from the specified URL.
     *
     * @param uri the link of the image to open
     * @param args optional arguments. {@link #USERTYPE_ARG} to specify the type of user (see {@link Credentials.UserType}
     *             and {@link #USERNAME_ARG} to specify the username when connecting to the OMERO server
     * @return an {@link OmeroImageServer} to open the provided image, or null if the image server creation failed
     * @throws IOException if a login form cannot be created
     */
    @Override
    public ImageServer<BufferedImage> buildServer(URI uri, String... args) throws IOException {
        return createOrGetClient(uri, args)
                .map(client -> OmeroImageServer.create(uri, client, args).join())  //TODO: handle exception
                .orElse(null);
    }

    /**
     * Check whether a URI is supported by this builder.
     *
     * @param entityURI the link of the image to open
     * @param args optional arguments. {@link #USERTYPE_ARG} to specify the type of user (see {@link Credentials.UserType}
     *             and {@link #USERNAME_ARG} to specify the username when connecting to the OMERO server
     * @return some information about the images that can be opened with an OMERO image server from the provided URI
     * @throws IOException if a login form cannot be created
     */
    @Override
    public ImageServerBuilder.UriImageSupport<BufferedImage> checkImageSupport(URI entityURI, String... args) throws IOException {
        Optional<Client> client = createOrGetClient(entityURI, args);

        if (client.isPresent()) {
            try {
                List<ImageServerBuilder.ServerBuilder<BufferedImage>> builders = client.get().getApisHandler().getImagesURIFromEntityURI(
                                entityURI
                        )
                        .join()
                        .stream()
                        .map(uri -> createServerBuilder(client.get(), uri, args))
                        .map(CompletableFuture::join)
                        .toList();

                return UriImageSupport.createInstance(
                        this.getClass(),
                        builders.isEmpty() ? 0 : SUPPORT_LEVEL,
                        builders
                );
            } catch (Exception e) {
                logger.debug("Error when getting image URIs", e);

                return UriImageSupport.createInstance(
                        this.getClass(),
                        0,
                        List.of()
                );
            }
        } else {
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

    private static Optional<Client> createOrGetClient(URI uri, String... args) throws IOException {
        Optional<Credentials.UserType> userType = Utils.getCredentialFromArgs(USERTYPE_ARG, args)
                .flatMap(usertypeFromArgs -> Arrays.stream(Credentials.UserType.values())
                        .filter(type -> type.name().equals(usertypeFromArgs))
                        .findAny()
                );
        if (userType.isPresent() && userType.get().equals(Credentials.UserType.PUBLIC_USER)) {
            try {
                return Optional.of(Client.createOrGet(uri.toString(), new Credentials()));
            } catch (Exception e) {
                logger.debug("Cannot create client of {}", uri, e);
                return Optional.empty();
            }
        }

        Optional<String> username = Utils.getCredentialFromArgs(USERNAME_ARG, args);

        URI webServerUri;
        try {
            webServerUri = Utils.getServerURI(uri);
        } catch (URISyntaxException e) {
            logger.debug("Cannot get server URI from {}", uri);
            return Optional.empty();
        }

        if (UiUtilities.usingGUI()) {
            LoginForm loginForm = new LoginForm(
                    QuPathGUI.getInstance().getStage(),
                    webServerUri,
                    username.map(user ->
                            new Credentials(Credentials.UserType.REGULAR_USER, user, null)
                    ).orElse(null),
                    client -> {

                    });
            loginForm.showAndWait();
            return loginForm.getCreatedClient();
        } else {
            try {
                return Optional.of(Client.createOrGet(uri.toString(), CommandLineAuthenticator.authenticate(webServerUri, username.orElse(null))));
            } catch (Exception e) {
                logger.debug("Cannot create client of {}", uri, e);
                return Optional.empty();
            }
        }
    }

    private static CompletableFuture<ImageServerBuilder.ServerBuilder<BufferedImage>> createServerBuilder(
            Client client,
            URI uri,
            String... args
    ) {
        return client.getApisHandler()
                .getImageMetadata(ApisHandler
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
