package qupath.ext.omero.gui.browser;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.ext.omero.core.preferences.ServerPreference;
import qupath.ext.omero.gui.login.LoginForm;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Menu allowing to create a connection with a new server using a {@link LoginForm}, or to browse an already
 * connected server (see {@link BrowserCommand}).
 */
public class BrowseMenu extends Menu implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(BrowseMenu.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final Map<URI, BrowserCommand> browserCommands = new HashMap<>();
    private final MenuItem newServerItem = new MenuItem(resources.getString("Browser.BrowseMenu.newServer"));
    private final ListChangeListener<? super ServerPreference> preferencesListener = ignored -> Platform.runLater(this::createURIItems);
    private final Stage owner;
    private LoginForm loginForm;

    /**
     * Creates the browse menu.
     *
     * @param owner the window owning this menu
     */
    public BrowseMenu(Stage owner) {
        logger.debug("Creating browse menu");
        this.owner = owner;

        setText(resources.getString("Browser.BrowseMenu.browseServer"));

        newServerItem.setOnAction(ignoredEvent -> {
            if (loginForm == null) {
                logger.debug("Login form not created. Creating and showing it");
                try {
                    loginForm = new LoginForm(
                            owner,
                            client -> Platform.runLater(() ->
                                    getBrowserCommand(client.getApisHandler().getWebServerURI()).run()
                            )
                    );
                    loginForm.show();
                } catch (IOException e) {
                    logger.error("Error while creating the login server form", e);
                }
            } else {
                logger.debug("Login form already created. Showing it");
                loginForm.show();
                loginForm.requestFocus();
            }
        });

        createURIItems();

        PreferencesManager.addServerPreferencesListener(preferencesListener);
    }

    @Override
    public void close() {
        PreferencesManager.removeServerPreferencesListener(preferencesListener);
    }

    /**
     * Open the browser of the provided client. It will be created if it doesn't already exist.
     *
     * @param client the client that should be displayed in the browser to open
     */
    public void openBrowserOfClient(Client client) {
        getBrowserCommand(client.getApisHandler().getWebServerURI()).run();
    }

    private void createURIItems() {
        List<ServerPreference> serverPreferences = PreferencesManager.getServerPreferences();
        logger.debug("Resetting URI items to show {}", serverPreferences);

        getItems().clear();

        for (ServerPreference serverPreference: serverPreferences) {
            logger.debug("Adding URI item for {}", serverPreference.webServerUri());

            BrowserCommand browserCommand = getBrowserCommand(serverPreference.webServerUri());

            MenuItem clientMenuItem = new MenuItem(serverPreference.webServerUri().toString());
            clientMenuItem.setOnAction(e -> browserCommand.run());
            getItems().add(clientMenuItem);
        }

        if (!getItems().isEmpty()) {
            getItems().add(new SeparatorMenuItem());
        }

        getItems().add(newServerItem);
    }

    private BrowserCommand getBrowserCommand(URI uri) {
        if (!browserCommands.containsKey(uri)) {
            browserCommands.put(
                    uri,
                    new BrowserCommand(uri, owner, this::openBrowserOfClient)
            );
        }

        return browserCommands.get(uri);
    }
}
