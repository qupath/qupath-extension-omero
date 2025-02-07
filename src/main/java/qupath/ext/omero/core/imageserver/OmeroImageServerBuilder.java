package qupath.ext.omero.core.imageserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.gui.login.LoginForm;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@link ImageServerBuilder Image server builder} of the OMERO extension.
 * <p>
 * It creates an {@link OmeroImageServer}.
 */
public class OmeroImageServerBuilder implements ImageServerBuilder<BufferedImage> {

    private static final Logger logger = LoggerFactory.getLogger(OmeroImageServerBuilder.class);
    private static final String USERTYPE_ARG = "--usertype";
    private static final String USERNAME_ARG = "--username";
    private static final String PIXEL_API_ARG = "--pixelAPI";
    private static final float SUPPORT_LEVEL = 4;
    private record ClientPixelApiArgsWrapper(Client client, PixelApi pixelApi, List<String> args) {}

    /**
     * Attempt to create a {@link OmeroImageServer} from the specified URL.
     *
     * @param imageUri the link of the image to open
     * @param args optional arguments. {@link #USERTYPE_ARG} to specify the type of user (see {@link Credentials.UserType}),
     *             {@link #USERNAME_ARG} to specify the username when connecting to the OMERO server, {@link #PIXEL_API_ARG}
     *             to define the pixel API to use, and additional arguments specified in the chosen pixel API of
     *             {@link qupath.ext.omero.core.pixelapis}. These arguments will be updated with the new values provided
     *             by the user before being sent to the image server
     * @return an {@link OmeroImageServer} to open the provided image, or null if the image server creation failed
     * @throws IOException if a login form cannot be created
     */
    @Override
    public ImageServer<BufferedImage> buildServer(URI imageUri, String... args) throws IOException {
        return createOrGetClient(imageUri, Arrays.stream(args).toList())
                .map(clientPixelApiArgsWrapper -> {
                    try {
                        return new OmeroImageServer(
                                imageUri,
                                clientPixelApiArgsWrapper.client(),
                                clientPixelApiArgsWrapper.pixelApi(),
                                clientPixelApiArgsWrapper.args()
                        );
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }

                        logger.debug("Cannot create OMERO image server for {}", imageUri, e);
                        return null;
                    }
                })
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
    public UriImageSupport<BufferedImage> checkImageSupport(URI entityURI, String... args) throws IOException {
        Optional<ClientPixelApiArgsWrapper> clientArgsWrapper = createOrGetClient(entityURI, Arrays.stream(args).toList());

        if (clientArgsWrapper.isPresent()) {
            logger.debug("Client retrieved for {}. Getting images URIs...", entityURI);

            try {
                List<ServerBuilder<BufferedImage>> builders = clientArgsWrapper.get().client().getApisHandler().getImagesURIFromEntityURI(
                                entityURI
                        )
                        .join()
                        .stream()
                        .map(uri -> createServerBuilder(clientArgsWrapper.get().client(), uri, clientArgsWrapper.get().args()))
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
            logger.debug("Cannot create client for {}. Assuming OMERO doesn't support it", entityURI);

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

    private static Optional<ClientPixelApiArgsWrapper> createOrGetClient(URI uri, List<String> args) throws IOException {
        Optional<Credentials.UserType> userType = ArgsUtils.findArgInList(USERTYPE_ARG, args)
                .flatMap(usertypeFromArgs -> Arrays.stream(Credentials.UserType.values())
                        .filter(type -> type.name().equals(usertypeFromArgs))
                        .findAny()
                );
        Optional<String> username = ArgsUtils.findArgInList(USERNAME_ARG, args);

        Client client = null;
        if (userType.isPresent()) {
            logger.debug("User type {} found in arguments", userType.get());

            switch (userType.get()) {
                case PUBLIC_USER -> {
                    try {
                        client = Client.createOrGet(uri.toString(), new Credentials());
                    } catch (Exception e) {
                        logger.debug("Cannot create client of {}", uri, e);
                    }
                }
                case REGULAR_USER -> {
                    if (username.isPresent()) {
                        logger.debug("Username {} found in arguments", username.get());

                        Optional<Client> existingClient = Client.getClients().stream()
                                .filter(c -> c.getApisHandler().getWebServerURI().getHost().equals(uri.getHost()))
                                .filter(c -> c.getApisHandler().getCredentials().equals(
                                        new Credentials(username.get(), null)
                                ))
                                .findAny();
                        if (existingClient.isPresent()) {
                            logger.debug("Existing client of {} with username {} found. Using it", uri, username.get());
                            client = existingClient.get();
                        } else {
                            logger.debug("No client of {} with username {} was found. Prompting credentials...", uri, username.get());
                            client = getClientFromUserPrompt(uri, username.get());
                        }
                    } else {
                        logger.debug("Username not found in arguments. Prompting credentials...");
                        client = getClientFromUserPrompt(uri, null);
                    }
                }
            }
        } else {
            logger.debug("User type not found in arguments");

            Optional<Client> existingClient = Client.getClients().stream()
                    .filter(c -> c.getApisHandler().getWebServerURI().getHost().equals(uri.getHost()))
                    .findAny();
            if (existingClient.isPresent()) {
                logger.debug("Existing client of {} found. Using it", uri);
                client = existingClient.get();
            } else {
                logger.debug("No client of {} was found. Prompting credentials...", uri);
                client = getClientFromUserPrompt(uri, null);
            }
        }

        if (client == null) {
            return Optional.empty();
        } else {
            PixelApi pixelApi = getPixelAPIFromArgs(client, args).orElse(null);
            if (pixelApi == null) {
                pixelApi = client.getSelectedPixelApi().get();

                if (pixelApi == null) {
                    logger.debug("No supplied pixel API and no selected pixel API");
                    return Optional.empty();
                } else {
                    logger.debug("No pixel API was found in the arguments, so {} was selected to read {}", pixelApi.getName(), uri);
                }
            }

            Map<String, String> argumentsToReplace = new HashMap<>();
            argumentsToReplace.put(USERTYPE_ARG, client.getApisHandler().getCredentials().userType().name());
            if (client.getApisHandler().getCredentials().username() != null) {
                argumentsToReplace.put(USERNAME_ARG, client.getApisHandler().getCredentials().username());
            }
            argumentsToReplace.put(PIXEL_API_ARG, pixelApi.getName());
            argumentsToReplace.putAll(pixelApi.getArgs());

            return Optional.of(new ClientPixelApiArgsWrapper(client, pixelApi, ArgsUtils.replaceArgs(args, argumentsToReplace)));
        }
    }

    private static CompletableFuture<ServerBuilder<BufferedImage>> createServerBuilder(
            Client client,
            URI uri,
            List<String> args
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
                        args.toArray(new String[0])
                ));
    }

    private static Client getClientFromUserPrompt(URI uri, String username) {
        if (UiUtilities.usingGUI()) {
            logger.debug("Prompting credentials from GUI");

            return FXUtils.callOnApplicationThread(() -> {
                LoginForm loginForm = new LoginForm(
                        QuPathGUI.getInstance().getStage(),
                        uri,
                        username == null ? null : new Credentials(username, null),
                        c -> {}
                );

                loginForm.showAndWait();
                return loginForm.getCreatedClient().orElse(null);
            });
        } else {
            logger.debug("Prompting credentials from command line");

            try {
                return Client.createOrGet(
                        uri.toString(),
                        CommandLineAuthenticator.authenticate(uri, username)
                );
            } catch (Exception e) {
                logger.debug("Cannot create client of {}", uri, e);
                return null;
            }
        }
    }

    private static Optional<PixelApi> getPixelAPIFromArgs(Client client, List<String> args) {
        String pixelAPIName = null;
        int i = 0;
        while (i < args.size()-1) {
            String parameter = args.get(i++);
            if (PIXEL_API_ARG.equalsIgnoreCase(parameter.trim())) {
                pixelAPIName = args.get(i++).trim();
            }
        }

        if (pixelAPIName == null) {
            logger.debug("Pixel API not found in arguments");
        } else {
            for (PixelApi pixelAPI: client.getAllPixelApis()) {
                if (pixelAPI.getName().equalsIgnoreCase(pixelAPIName)) {
                    if (pixelAPI.isAvailable().get()) {
                        logger.debug("Pixel API '{}' found from arguments", pixelAPIName);
                    } else {
                        logger.warn(
                                "The provided pixel API ({}) was found but is not available at the moment. This may cause issues when using it",
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
