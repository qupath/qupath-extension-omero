package qupath.ext.omero.gui.connectionsmanager;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.ext.omero.core.preferences.ServerPreference;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

/**
 * The connection manager provides a window that displays the connections to all servers.
 * The user can connect, log in, log out, and remove a connection to a server.
 * <p>
 * Each connection is displayed using the {@link Connection} pane.
 */
public class ConnectionsManager extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionsManager.class);
    private final Consumer<Client> openClientBrowser;
    @FXML
    private VBox container;
    @FXML
    private Label noClients;

    /**
     * Creates the connection manager window.
     *
     * @param owner the stage that should own this window
     * @param openClientBrowser a function that will be called to request opening the browser of a client. It will be
     *                          called from the JavaFX Application Thread
     * @throws IOException if an error occurs while creating the window
     */
    public ConnectionsManager(Stage owner, Consumer<Client> openClientBrowser) throws IOException {
        logger.debug("Creating connections manager window");
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
        Client.addListenerToClients(() -> Platform.runLater(this::populate));
        PreferencesManager.addListenerToServerPreferences(() -> Platform.runLater(this::populate));

        noClients.visibleProperty().bind(Bindings.isEmpty(container.getChildren()));
        noClients.managedProperty().bind(noClients.visibleProperty());
    }

    private void populate() {
        List<Client> clients = Client.getClients();
        List<URI> serverPreferenceUris = PreferencesManager.getServerPreferences().stream().map(ServerPreference::webServerUri).toList();
        logger.debug("Populating {} and {} to connection manager children {}", clients, serverPreferenceUris, container.getChildren());

        List<Connection> connectionsToRemove = container.getChildren().stream()
                .filter(node -> node instanceof Connection)
                .map(node -> (Connection) node)
                .filter(connection -> connection.getClient()
                        .map(client -> !clients.contains(client))
                        .orElse(!serverPreferenceUris.contains(connection.getServerURI()) ||
                                clients.stream().map(client -> client.getApisHandler().getWebServerURI()).toList().contains(connection.getServerURI())
                        )
                )
                .toList();
        container.getChildren().removeAll(connectionsToRemove);
        logger.debug("Removed connections {}", connectionsToRemove.stream().map(Connection::getServerURI).toList());

        for (Client client: clients) {
            if (container.getChildren().stream()
                    .filter(node -> node instanceof Connection)
                    .map(node -> (Connection) node)
                    .noneMatch(connection -> client.equals(connection.getClient().orElse(null)))) {
                try {
                    container.getChildren().add(new Connection(this, client, openClientBrowser));
                    logger.debug("Added connection with client {}", client);
                } catch (IOException e) {
                    logger.error("Error while creating connection pane", e);
                }
            }
        }

        for (URI uri: serverPreferenceUris) {
            if (container.getChildren().stream()
                    .filter(node -> node instanceof Connection)
                    .map(node -> (Connection) node)
                    .noneMatch(connection -> uri.equals(connection.getServerURI()))) {
                try {
                    container.getChildren().add(new Connection(this, uri, openClientBrowser));
                    logger.debug("Added connection with URI {}", uri);
                } catch (IOException e) {
                    logger.error("Error while creating connection pane", e);
                }
            }
        }

        sizeToScene();
    }
}
