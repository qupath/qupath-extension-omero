package qupath.ext.omero.gui.connectionsmanager;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.ext.omero.core.preferences.ServerPreference;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The connection manager provides a window that displays the connections to all servers.
 * The user can connect, log in, log out, and remove a connection to a server.
 * <p>
 * Each connection is displayed using the {@link Connection} pane.
 */
public class ConnectionsManager extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionsManager.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final Consumer<Client> openClientBrowser;
    @FXML
    private VBox container;

    /**
     * Creates the connection manager window.
     *
     * @param owner the stage that should own this window
     * @param openClientBrowser a function that will be called to request opening the browser of a client
     * @throws IOException if an error occurs while creating the window
     */
    public ConnectionsManager(Stage owner, Consumer<Client> openClientBrowser) throws IOException {
        this.openClientBrowser = openClientBrowser;

        initUI(owner);
        setUpListeners();
    }

    private void initUI(Stage owner) throws IOException {
        UiUtilities.loadFXML(this, ConnectionsManager.class.getResource("connections_manager.fxml"));

        if (owner != null) {
            initOwner(owner);
        }
        populate();
        show();
    }

    private void setUpListeners() {
        Client.getClients().addListener((ListChangeListener<? super Client>) change ->
                Platform.runLater(this::populate)
        );
        PreferencesManager.getServerPreferences().addListener((ListChangeListener<? super ServerPreference>) change ->
                Platform.runLater(this::populate)
        );
    }

    private void populate() {
        container.getChildren().clear();

        Set<URI> urisAdded = new HashSet<>();
        for (Client client: Client.getClients()) {
            try {
                container.getChildren().add(new Connection(client, openClientBrowser));
                urisAdded.add(client.getApisHandler().getWebServerURI());
            } catch (IOException e) {
                logger.error("Error while creating connection pane", e);
            }
        }

        // Create copy to prevent modifications while iterating
        List<ServerPreference> preferences = new ArrayList<>(PreferencesManager.getServerPreferences());

        for (ServerPreference serverPreference: preferences) {
            if (!urisAdded.contains(serverPreference.webServerUri())) {
                try {
                    container.getChildren().add(new Connection(serverPreference.webServerUri(), openClientBrowser));
                } catch (IOException e) {
                    logger.error("Error while creating connection pane", e);
                }
            }
        }

        if (container.getChildren().isEmpty()) {
            container.getChildren().add(new Label(resources.getString("ConnectionsManager.ConnectionManager.noClients")));
        }

        sizeToScene();
    }
}
