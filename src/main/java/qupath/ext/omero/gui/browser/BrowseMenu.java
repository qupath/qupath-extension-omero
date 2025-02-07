package qupath.ext.omero.gui.browser;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.ext.omero.core.preferences.ServerPreference;
import qupath.ext.omero.gui.login.LoginForm;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Menu allowing to create a connection with a new server using a {@link LoginForm}, or to browse an already
 * connected server (see {@link BrowserCommand}).
 */
public class BrowseMenu extends Menu {

    private static final Logger logger = LoggerFactory.getLogger(BrowseMenu.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final Map<URI, BrowserCommand> browserCommands = new HashMap<>();
    private final MenuItem newServerItem = new MenuItem(resources.getString("Browser.BrowseMenu.newServer"));
    private final Stage owner;
    private LoginForm loginForm;

    /**
     * Creates the browse menu.
     *
     * @param owner the window owning this menu
     */
    public BrowseMenu(Stage owner) {
        this.owner = owner;

        initUI();
        setUpListeners();
    }

    /**
     * Open the browser of the provided client. It will be created if it doesn't already exist.
     *
     * @param client the client that should be displayed in the browser to open
     */
    public void openBrowserOfClient(Client client) {
        getBrowserCommand(client.getApisHandler().getWebServerURI()).run();
    }

    private void initUI() {
        setText(resources.getString("Browser.BrowseMenu.browseServer"));

        newServerItem.setOnAction(ignoredEvent -> {
            if (loginForm == null) {
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
                loginForm.show();
                loginForm.requestFocus();
            }
        });

        createURIItems();
    }

    private void setUpListeners() {
        PreferencesManager.getServerPreferences().addListener((ListChangeListener<? super ServerPreference>) change ->
                Platform.runLater(this::createURIItems)
        );
    }

    private void createURIItems() {
        getItems().clear();

        for (ServerPreference serverPreference: PreferencesManager.getServerPreferences()) {
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
