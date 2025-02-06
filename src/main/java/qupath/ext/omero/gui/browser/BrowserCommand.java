package qupath.ext.omero.gui.browser;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.ext.omero.gui.UiUtilities;
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
 */
class BrowserCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(BrowserCommand.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final URI uri;
    private final Stage owner;
    private final Consumer<Client> openClientBrowser;
    private Browser browser;
    private Client client;
    private LoginForm loginForm;

    /**
     * Creates a browser command.
     *
     * @param uri the URI of the web client which will be used by the browser to retrieve data from the corresponding OMERO server
     * @param owner the window owning this command
     * @param openClientBrowser a function that will be called to request opening the browser of a client
     */
    public BrowserCommand(URI uri, Stage owner, Consumer<Client> openClientBrowser) {
        this.uri = uri;
        this.owner = owner;
        this.openClientBrowser = openClientBrowser;

        Client.getClients().addListener((ListChangeListener<? super Client>) change -> Platform.runLater(() -> {
            if (client != null) {
                while (change.next()) {
                    if (change.wasRemoved()) {
                        if (change.getRemoved().contains(client)) {
                            if (browser != null) {
                                browser.close();
                            }
                            browser = null;
                            client = null;
                        }
                    }
                }
                change.reset();
            }
        }));
    }

    @Override
    public void run() {
        if (browser != null) {
            browser.show();
            browser.requestFocus();
            return;
        }

        Optional<Client> existingClient = Client.getClients().stream()
                .filter(client -> client.getApisHandler().getWebServerURI().equals(uri))
                .findAny();
        if (existingClient.isPresent()) {
            createBrowser(existingClient.get());
            return;
        }

        if (loginForm == null) {
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
            loginForm.show();
            loginForm.requestFocus();
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

        waitingWindow.show();
        client.getServer()
                .exceptionally(error -> {
                    logger.error("Cannot get server information on the {}", uri, error);
                    return null;
                })
                .thenAccept(server -> Platform.runLater(() -> {
                    waitingWindow.close();

                    if (server == null) {
                        try {
                            client.close();
                        } catch (Exception e) {
                            logger.error("Error while closing client", e);
                        }

                        Dialogs.showErrorMessage(
                                resources.getString("Browser.BrowserCommand.omeroError"),
                                MessageFormat.format(
                                        resources.getString("Browser.BrowserCommand.cannotGetServerInformation"),
                                        uri
                                )
                        );
                    } else {
                        try {
                            this.browser = new Browser(owner, client, server, openClientBrowser);
                            this.client = client;
                        } catch (IOException e) {
                            logger.error("Error while creating the browser", e);
                        }
                    }
                }));
    }
}
