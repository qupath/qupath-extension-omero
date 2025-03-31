package qupath.ext.omero.core.imageserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.ArgsUtils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.gui.login.LoginForm;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * {@link ImageServerBuilder Image server builder} of the OMERO extension.
 * <p>
 * It creates an {@link OmeroImageServer}.
 */
public class OmeroImageServerBuilder implements ImageServerBuilder<BufferedImage> {

    private static final Logger logger = LoggerFactory.getLogger(OmeroImageServerBuilder.class);
    private static final String USERTYPE_ARG = "--usertype";
    private static final String USERNAME_ARG = "--username";
    private static final String PASSWORD_ARG = "--password";
    private static final String PIXEL_API_ARG = "--pixelAPI";
    private static final float SUPPORT_LEVEL = 4;
    private static final List<String> ACCEPTED_SCHEMES = List.of("http", "https");
    private record ClientPixelApiArgsWrapper(Client client, PixelApi pixelApi, List<String> args) {}

    /**
     * Attempt to create a {@link OmeroImageServer} from the specified URL.
     *
     * @param imageUri the link of the image to open
     * @param args optional arguments. {@link #USERTYPE_ARG} to specify the type of user (see {@link Credentials.UserType}),
     *             {@link #USERNAME_ARG} to specify the username when connecting to the OMERO server, {@link #PASSWORD_ARG}
     *             to specify the password when connecting to the OMERO (note that it is not recommended to use this parameter
     *             as it may be revealed. It will not be saved in the args of the image server), {@link #PIXEL_API_ARG} to
     *             define the pixel API to use, and additional arguments specified in the chosen pixel API of
     *             {@link qupath.ext.omero.core.pixelapis}. These arguments will be updated with the new values provided by
     *             the user before being sent to the image server
     * @return an {@link OmeroImageServer} to open the provided image, or null if the image server creation failed
     */
    @Override
    public ImageServer<BufferedImage> buildServer(URI imageUri, String... args) {
        logger.debug("Building OMERO server for {} with args {}...", imageUri, Arrays.toString(args));

        return getClientAndPixelApi(imageUri, Arrays.stream(args).toList())
                .map(clientPixelApiArgsWrapper -> {
                    try {
                        return new OmeroImageServer(
                                imageUri,
                                clientPixelApiArgsWrapper.client(),
                                clientPixelApiArgsWrapper.pixelApi(),
                                clientPixelApiArgsWrapper.args()
                        );
                    } catch (Exception e) {
                        logger.debug("Cannot create OMERO image server for {}", imageUri, e);

                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }

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
     */
    @Override
    public UriImageSupport<BufferedImage> checkImageSupport(URI entityURI, String... args) {
        logger.debug("Checking OMERO support for {} with args {}...", entityURI, Arrays.toString(args));

        Optional<ClientPixelApiArgsWrapper> clientArgsWrapper = getClientAndPixelApi(entityURI, Arrays.stream(args).toList());

        if (clientArgsWrapper.isEmpty()) {
            return UriImageSupport.createInstance(
                    this.getClass(),
                    0,
                    List.of()
            );
        }

        logger.debug("Client retrieved for {}. Getting images URIs...", entityURI);
        try {
            List<ServerBuilder<BufferedImage>> builders = clientArgsWrapper.get().client().getApisHandler().getImagesURIFromEntityURI(
                            entityURI
                    )
                    .join()
                    .stream()
                    .map(uri -> createServerBuilder(uri, clientArgsWrapper.get()))
                    .flatMap(Optional::stream)
                    .toList();
            logger.debug("Got builders {} for {}", builders, entityURI);

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

    private static Optional<ClientPixelApiArgsWrapper> getClientAndPixelApi(URI uri, List<String> args) {
        if (!ACCEPTED_SCHEMES.contains(uri.getScheme())) {
            logger.debug("{} doesn't contain one of the required schemes: {}. Cannot open image with OMERO image server", uri, ACCEPTED_SCHEMES);
            return Optional.empty();
        }

        try {
            return getClient(uri, args).map(client -> {
                logger.debug("Got client {} to open {}", client, uri);

                PixelApi pixelApi = getPixelAPIFromArgs(client, args).orElse(null);
                if (pixelApi == null) {
                    pixelApi = client.getSelectedPixelApi().get();

                    if (pixelApi == null) {
                        logger.debug("No supplied pixel API and no selected pixel API. Can't open {}", uri);
                        return null;
                    } else {
                        logger.debug("No pixel API was found in the arguments, so {} was selected to read {}", pixelApi.getName(), uri);
                    }
                }

                Map<String, String> argumentsToReplace = new HashMap<>();
                argumentsToReplace.put(USERTYPE_ARG, client.getApisHandler().getCredentials().userType().name());
                if (client.getApisHandler().getCredentials().username() != null) {
                    argumentsToReplace.put(USERNAME_ARG, client.getApisHandler().getCredentials().username());
                    argumentsToReplace.put(PASSWORD_ARG, null);
                }
                argumentsToReplace.put(PIXEL_API_ARG, pixelApi.getName());
                argumentsToReplace.putAll(pixelApi.getArgs());

                return new ClientPixelApiArgsWrapper(client, pixelApi, ArgsUtils.replaceArgs(args, argumentsToReplace));
            });
        } catch (Exception e) {
            logger.debug("Cannot create OMERO client for {}", uri, e);

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            return Optional.empty();
        }
    }

    private static Optional<ServerBuilder<BufferedImage>> createServerBuilder(URI uri, ClientPixelApiArgsWrapper clientPixelApiArgsWrapper) {
        logger.debug("Creating server builder for {} with {}", uri, clientPixelApiArgsWrapper);

        try (OmeroImageServer imageServer = new OmeroImageServer(
                uri,
                clientPixelApiArgsWrapper.client,
                clientPixelApiArgsWrapper.pixelApi,
                clientPixelApiArgsWrapper.args
        )) {
            return Optional.of(DefaultImageServerBuilder.createInstance(
                    OmeroImageServerBuilder.class,
                    imageServer.getMetadata(),
                    uri,
                    clientPixelApiArgsWrapper.args.toArray(new String[0])
            ));
        } catch (Exception e) {
            logger.debug("Cannot create image server for {}", uri, e);

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            return Optional.empty();
        }
    }

    private static Optional<Client> getClient(URI uri, List<String> args) throws URISyntaxException, ExecutionException, InterruptedException {
        logger.debug("Getting or creating client to open {} with args {}", uri, args);

        Optional<Client> existingClient = getExistingClient(uri);
        if (existingClient.isPresent()) {
            logger.debug("Found existing client {} to open {}", existingClient.get(), uri);
            return existingClient;
        } else {
            logger.debug("No existing client found to open {}. Creating new one", uri);
        }

        Optional<Credentials.UserType> userType = ArgsUtils.findArgInList(USERTYPE_ARG, args)
                .flatMap(usertypeFromArgs -> Arrays.stream(Credentials.UserType.values())
                        .filter(type -> type.name().equals(usertypeFromArgs))
                        .findAny()
                );
        Optional<String> username = ArgsUtils.findArgInList(USERNAME_ARG, args);
        Optional<String> password = ArgsUtils.findArgInList(PASSWORD_ARG, args);

        if (userType.isPresent() && userType.get().equals(Credentials.UserType.PUBLIC_USER)) {
            logger.debug("Public user type found in arguments. Using it to connect");

            try {
                return Optional.of(Client.createOrGet(uri.toString(), new Credentials(), UiUtilities::displayPingErrorDialogIfUiPresent));
            } catch (Exception e) {
                logger.debug("Cannot create client of {}", uri, e);

                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                return Optional.empty();
            }
        } else {
            logger.debug("User type not found or not equal to public user. Using username/password to connect");

            if (username.isPresent()) {
                logger.debug("Username {} found in arguments", username.get());

                if (password.isPresent()) {
                    logger.debug("Password found in arguments. Using it and the username {} to connect", username.get());
                    return Optional.of(Client.createOrGet(
                            uri.toString(),
                            new Credentials(username.get(), password.get().toCharArray()),
                            UiUtilities::displayPingErrorDialogIfUiPresent
                    ));
                } else {
                    logger.debug("Password not found in arguments. Prompting credentials with user {}...", username.get());
                    return getClientFromUserPrompt(uri, username.get());
                }
            } else {
                logger.debug("Username not found in arguments. Prompting credentials...");
                return getClientFromUserPrompt(uri, null);
            }
        }
    }

    private static Optional<PixelApi> getPixelAPIFromArgs(Client client, List<String> args) {
        logger.debug("Getting pixel API from args {}", args);

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
            logger.debug("Pixel API {} found in argument. Determining if it actually exists with {}", pixelAPIName, client);

            for (PixelApi pixelAPI: client.getAllPixelApis()) {
                if (pixelAPI.getName().equalsIgnoreCase(pixelAPIName)) {
                    if (pixelAPI.isAvailable().get()) {
                        logger.debug("Pixel API '{}' found from arguments and exists with {}", pixelAPI, client);
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
                    "The provided pixel API ({}) was not recognized among the pixel APIs of this client ({}). Another one will be used",
                    pixelAPIName,
                    client.getAllPixelApis()
            );
        }

        return Optional.empty();
    }

    private static Optional<Client> getExistingClient(URI uri) {
        ServerEntity entity = ApisHandler.parseEntity(uri).orElseThrow(() -> new NoSuchElementException(String.format(
                "The provided URI %s was not recognized",
                uri
        )));

        List<Client> clients = Client.getClients();
        logger.debug("Finding if existing client belonging to {} can access {}", clients, uri);

        return clients.stream()
                .filter(client -> client.getApisHandler().getWebServerURI().getHost().equals(uri.getHost()))
                .map(client -> {
                    try {
                        URI entityUri = new URI(client.getApisHandler().getEntityUri(entity));
                        client.getApisHandler().isLinkReachable(entityUri, RequestSender.RequestType.GET).get();

                        logger.debug("{} is reachable. {} can access entity with ID {}. Using it", entityUri, client, entity.getId());
                        return client;
                    } catch (ExecutionException | InterruptedException | URISyntaxException e) {
                        logger.debug("{} cannot access entity with ID {}. Skipping it", client, entity.getId(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findAny();
    }

    private static Optional<Client> getClientFromUserPrompt(URI uri, String username) {
        if (UiUtilities.usingGUI()) {
            logger.debug("Prompting credentials from GUI to connect to {}", uri);

            return FXUtils.callOnApplicationThread(() -> {
                LoginForm loginForm = new LoginForm(
                        QuPathGUI.getInstance().getStage(),
                        uri,
                        username == null ? null : new Credentials(username, null),
                        c -> {}
                );

                loginForm.showAndWait();
                return loginForm.getCreatedClient();
            });
        } else {
            logger.debug("Prompting credentials from command line to connect to {}", uri);

            try {
                return Optional.of(Client.createOrGet(
                        uri.toString(),
                        CommandLineAuthenticator.authenticate(uri, username),
                        UiUtilities::displayPingErrorDialogIfUiPresent
                ));
            } catch (Exception e) {
                logger.debug("Cannot create client of {}", uri, e);

                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                return Optional.empty();
            }
        }
    }
}
