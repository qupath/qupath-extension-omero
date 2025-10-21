package qupath.ext.omero.gui.browser;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.ext.omero.gui.login.LoginForm;
import qupath.ext.omero.gui.login.WaitingWindow;
import qupath.fx.dialogs.Dialogs;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Command that starts a {@link Browser browser} corresponding to a URI.
 * <p>
 * This command must be {@link #close() closed} once no longer used.
 */
class BrowserCommand implements Runnable, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(BrowserCommand.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final URI uri;
    private final Stage owner;
    private final Consumer<Client> onClientCreated;
    private final ListChangeListener<? super Client> clientListener;
    private Browser browser;
    private Client client;
    private LoginForm loginForm;

    /**
     * Creates a browser command.
     *
     * @param uri the URI of the web client which will be used by the browser to retrieve data from the corresponding OMERO server
     * @param owner the window owning this command
     * @param onClientCreated a function that will be called if this browser command creates a new client. It will be called from
     *                        the JavaFX Application Thread
     */
    public BrowserCommand(URI uri, Stage owner, Consumer<Client> onClientCreated) {
        logger.debug("Creating browser command for {}", uri);
        this.uri = uri;
        this.owner = owner;
        this.onClientCreated = onClientCreated;
        this.clientListener = change -> Platform.runLater(() -> {
            if (client != null && !Client.getClients().contains(client)) {
                if (browser != null) {
                    browser.close();
                    browser.hide();
                }
                browser = null;
                client = null;
            }
        });

        Client.addClientsListener(clientListener);
    }

    @Override
    public void run() {
        if (browser != null) {
            logger.debug("Browser already exists. Showing it");
            browser.show();
            browser.requestFocus();
            return;
        }

        Optional<Client> existingClient = Client.getClients().stream()
                .filter(client -> client.getApisHandler().getWebServerUri().equals(uri))
                .findAny();
        if (existingClient.isPresent()) {
            logger.debug("Browser doesn't exist but client {} already exists. Using it to create browser", existingClient.get());
            createBrowser(existingClient.get());
            return;
        }

        if (loginForm == null) {
            logger.debug("Browser doesn't exist and login form doesn't exist. Creating and showing login form");
            try {
                loginForm = new LoginForm(
                        owner,
                        uri,
                        PreferencesManager.getCredentials(uri).orElse(null),
                        client -> Platform.runLater(() -> createBrowser(client))
                );
                loginForm.show();
            } catch (IOException e) {
                logger.error("Error while creating the login server form", e);
            }
        } else {
            logger.debug("Browser doesn't exist but login form already exists. Showing it");
            loginForm.show();
            loginForm.requestFocus();
        }
    }

    @Override
    public void close() {
        Client.removeClientsListener(clientListener);

        if (browser != null) {
            browser.close();
        }
    }

    private void createBrowser(Client client) {
        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    owner,
                    MessageFormat.format(
                            resources.getString("Browser.BrowserCommand.gatheringInformation"),
                            uri
                    )
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window");
            return;
        }

        logger.debug("Getting server to create browser for {}", client);
        waitingWindow.show();
        client.getServer().whenComplete((server, error) -> {
            Platform.runLater(waitingWindow::close);

            if (server == null) {
                logger.error("Cannot get server information on {}, so cannot create browser. Closing client", client, error);

                try {
                    client.close();
                } catch (Exception e) {
                    logger.error("Error while closing client", e);

                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }

                Platform.runLater(() -> Dialogs.showErrorMessage(
                        resources.getString("Browser.BrowserCommand.omeroError"),
                        MessageFormat.format(
                                resources.getString("Browser.BrowserCommand.cannotGetServerInformation"),
                                uri
                        )
                ));
                return;
            }

            logger.debug("Server {} of {} retrieved. Creating corresponding browser", server, client);
            Platform.runLater(() -> {
                try {
                    this.browser = new Browser(owner, client, server, onClientCreated);
                    this.client = client;
                } catch (IOException e) {
                    logger.error("Error while creating the browser", e);
                }
            });
        });
    }
}
