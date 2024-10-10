package qupath.ext.omero.gui.browser;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.gui.browser.newserver.NewServerForm;
import qupath.ext.omero.gui.browser.serverbrowser.BrowserCommand;
import qupath.fx.dialogs.Dialogs;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * <p>
 *     Menu allowing to create a connection with a new server
 *     (see the {@link qupath.ext.omero.gui.browser.newserver new server} package), or to browse
 *     an already connected server (see the {@link qupath.ext.omero.gui.browser.serverbrowser browse server} package).
 * </p>
 * <p>
 *     This class uses a {@link BrowseMenuModel} to update its state.
 * </p>
 */
public class BrowseMenu extends Menu {

    private static final Logger logger = LoggerFactory.getLogger(BrowseMenu.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final Map<URI, BrowserCommand> browserCommands = new HashMap<>();
    private MenuItem newServerItem;

    /**
     * Creates the browse menu.
     */
    public BrowseMenu() {
        initUI();
        setUpListeners();
    }

    public void openBrowserOfClient(URI uri) {
        if (browserCommands.containsKey(uri)) {
            browserCommands.get(uri).run();
        }
    }

    private void initUI() {
        setText(resources.getString("Browser.BrowseMenu.browseServer"));
        createURIItems();
        createNewServerItem();
    }

    private void setUpListeners() {
        BrowseMenuModel.getURIs().addListener((ListChangeListener<? super URI>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    for (URI uri: change.getRemoved()) {
                        if (browserCommands.containsKey(uri)) {
                            browserCommands.get(uri).close();
                            browserCommands.remove(uri);
                        }
                    }
                }
            }

            createURIItems();
            getItems().add(newServerItem);
        });
    }

    private void createURIItems() {
        getItems().clear();

        for (URI uri: BrowseMenuModel.getURIs()) {
            BrowserCommand browserCommand = getBrowserCommand(uri);

            MenuItem clientMenuItem = new MenuItem(uri.toString());
            clientMenuItem.setOnAction(e -> browserCommand.run());
            getItems().add(clientMenuItem);
        }

        if (!getItems().isEmpty()) {
            getItems().add(new SeparatorMenuItem());
        }
    }

    private void createNewServerItem() {
        newServerItem = new MenuItem(resources.getString("Browser.BrowseMenu.newServer"));
        newServerItem.setOnAction(ignoredEvent -> {
            try {
                NewServerForm newServerForm = new NewServerForm();

                boolean dialogConfirmed = Dialogs.showConfirmDialog(resources.getString("Browser.BrowseMenu.enterURL"), newServerForm);

                if (dialogConfirmed) {
                    String url = newServerForm.getURL();

                    //TODO: if error instance of URISyntaxException, Browser.BrowseMenu.invalidURI
                    //TODO: create custom exception for Browser.BrowseMenu.alreadyCreating?
                    WebClients.createClient(
                            url,
                            newServerForm.canSkipAuthentication() ? WebClient.Authentication.TRY_TO_SKIP : WebClient.Authentication.ENFORCE
                    ).thenAccept(client -> Platform.runLater(() -> {
                        if (client.getStatus().equals(WebClient.Status.SUCCESS)) {
                            getBrowserCommand(client.getApisHandler().getWebServerURI()).run();
                        } else if (client.getStatus().equals(WebClient.Status.FAILED)) {
                            Optional<WebClient.FailReason> failReason = client.getFailReason();
                            String message = null;

                            if (failReason.isPresent()) {
                                if (failReason.get().equals(WebClient.FailReason.INVALID_URI_FORMAT)) {
                                    message = MessageFormat.format(resources.getString("Browser.BrowseMenu.invalidURI"), url);
                                } else if (failReason.get().equals(WebClient.FailReason.ALREADY_CREATING)) {
                                    message = MessageFormat.format(resources.getString("Browser.BrowseMenu.alreadyCreating"), url);
                                }
                            } else {
                                message = MessageFormat.format(resources.getString("Browser.BrowseMenu.connectionFailed"), url);
                            }

                            if (message != null) {
                                Dialogs.showErrorMessage(
                                        resources.getString("Browser.BrowseMenu.webServer"),
                                        message
                                );
                            }
                        }
                    }));
                }
            } catch (IOException e) {
                logger.error("Error while creating the new server form", e);
            }
        });
        getItems().add(newServerItem);
    }

    private BrowserCommand getBrowserCommand(URI uri) {
        if (!browserCommands.containsKey(uri)) {
            browserCommands.put(uri, new BrowserCommand(uri));
        }

        return browserCommands.get(uri);
    }
}
