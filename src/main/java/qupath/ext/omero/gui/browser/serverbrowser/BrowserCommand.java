package qupath.ext.omero.gui.browser.serverbrowser;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.gui.browser.serverbrowser.newconnectionoptions.NewConnectionOptions;
import qupath.fx.dialogs.Dialogs;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Command that starts a {@link Browser browser} corresponding to a URI.
 */
public class BrowserCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(BrowserCommand.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final URI uri;
    private Browser browser;
    private WebClient client;

    /**
     * Creates a browser command.
     *
     * @param uri  the URI of the web client which will be used by the browser to retrieve data from the corresponding OMERO server
     */
    public BrowserCommand(URI uri) {
        this.uri = uri;

        BrowserModel.getClients().addListener((ListChangeListener<? super WebClient>) change -> {
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
            }
        });
    }

    @Override
    public void run() {
        if (browser == null) {
            Optional<WebClient> existingClient = WebClients.getClients().stream()
                    .filter(client -> client.getApisHandler().getWebServerURI().equals(uri))
                    .findAny();

            if (existingClient.isPresent()) {
                try {
                    browser = new Browser(existingClient.get());
                    this.client = existingClient.get();
                } catch (IOException e) {
                    logger.error("Error while creating the browser", e);
                }
            } else {
                NewConnectionOptions newConnectionOptions = null;
                try {
                    newConnectionOptions = new NewConnectionOptions();
                } catch (IOException e) {
                    logger.error("Error when creating the new connection options form", e);
                }

                boolean dialogConfirmed = newConnectionOptions != null && Dialogs.showConfirmDialog(
                        resources.getString("Browser.NewConnectionOptions.title"),
                        newConnectionOptions
                );

                if (dialogConfirmed) {
                    WebClients.createClient(uri.toString(), newConnectionOptions.canSkipAuthentication()).thenAccept(client -> Platform.runLater(() -> {
                        if (client.getStatus().equals(WebClient.Status.SUCCESS)) {
                            try {
                                browser = new Browser(client);
                                this.client = client;
                            } catch (IOException e) {
                                logger.error("Error while creating the browser", e);
                            }
                        } else if (client.getStatus().equals(WebClient.Status.FAILED)) {
                            Optional<WebClient.FailReason> failReason = client.getFailReason();
                            String message = null;

                            if (failReason.isPresent()) {
                                if (failReason.get().equals(WebClient.FailReason.INVALID_URI_FORMAT)) {
                                    message = MessageFormat.format(resources.getString("Browser.BrowserCommand.invalidURI"), uri.toString());
                                } else if (failReason.get().equals(WebClient.FailReason.ALREADY_CREATING)) {
                                    message = MessageFormat.format(resources.getString("Browser.BrowserCommand.alreadyCreating"), uri.toString());
                                }
                            } else {
                                message = MessageFormat.format(resources.getString("Browser.BrowserCommand.connectionFailed"), uri.toString());
                            }

                            if (message != null) {
                                Dialogs.showErrorMessage(
                                        resources.getString("Browser.BrowserCommand.webServer"),
                                        message
                                );
                            }
                        }
                    }));
                }
            }
        } else {
            UiUtilities.showWindow(browser);
        }
    }

    /**
     * Close the corresponding browser.
     */
    public void close() {
        if (browser != null) {
            browser.close();
        }
    }
}
