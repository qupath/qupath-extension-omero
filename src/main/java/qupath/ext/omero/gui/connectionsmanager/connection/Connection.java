package qupath.ext.omero.gui.connectionsmanager.connection;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.SetChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.gui.OmeroExtension;
import qupath.ext.omero.core.ClientsPreferencesManager;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.gui.UiUtilities;
import qupath.fx.dialogs.Dialogs;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

/**
 * <p>
 *     Pane displaying the status of a connection to a server as well as buttons to
 *     connect, log in, log out, or remove the connection.
 * </p>
 * <p>
 *     It also displays the list of images of this server that are currently opened using the
 *     {@link Image Image} label.
 * </p>
 * <p>
 *     This class uses a {@link ConnectionModel} to update its state.
 * </p>
 */
public class Connection extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(Connection.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final WebClient client;
    private final URI serverURI;
    private final ConnectionModel connectionModel;
    @FXML
    private Label uri;
    @FXML
    private HBox buttons;
    @FXML
    private Button browse;
    @FXML
    private Button connect;
    @FXML
    private Button login;
    @FXML
    private Button logout;
    @FXML
    private Button disconnect;
    @FXML
    private Button remove;
    @FXML
    private TitledPane imagesPane;
    @FXML
    private VBox imagesContainer;

    /**
     * Creates the connection pane using a {@link WebClient WebClient}.
     * Since a WebClient is present, there is already a connection with the server, so the user will have the possibility
     * to log in, log out, or remove the connection, but not to connect to the server.
     *
     * @param client  the client corresponding to the connection with the server
     * @throws IOException if an error occurs while creating the pane
     */
    public Connection(WebClient client) throws IOException {
        this(client, client.getApisHandler().getWebServerURI());
    }

    /**
     * Creates the connection pane using the URI of a server.
     * Since there is no WebClient, there is no connection with the server for now, so the user will have the possibility
     * to connect to the server or to remove the connection, but not to log in or log out.
     * Logging in or logging out will only be possible after a connection to the server is made.
     *
     * @param serverURI  the URI of the server
     * @throws IOException if an error occurs while creating the pane
     */
    public Connection(URI serverURI) throws IOException {
        this(null, serverURI);
    }

    private Connection(WebClient client, URI serverURI) throws IOException {
        this.client = client;
        this.serverURI = serverURI;

        if (client == null) {
            connectionModel = null;
        } else {
            connectionModel = new ConnectionModel(client);
        }

        initUI();
        setUpListeners();
    }

    @FXML
    private void onBrowseClicked(ActionEvent ignoredEvent) {
        if (client != null) {
            OmeroExtension.getBrowseMenu().openBrowserOfClient(client.getApisHandler().getWebServerURI());
        }
    }

    @FXML
    private void onConnectClicked(ActionEvent ignoredEvent) {
        NewConnectionOptions newConnectionOptions = null;
        try {
            newConnectionOptions = new NewConnectionOptions();
        } catch (IOException e) {
            logger.error("Error when creating the new connection options form", e);
        }

        boolean dialogConfirmed = newConnectionOptions != null && Dialogs.showConfirmDialog(
                resources.getString("ConnectionsManager.NewConnectionOptions.title"),
                newConnectionOptions
        );

        if (dialogConfirmed) {
            WebClients.createClient(
                    serverURI.toString(),
                    newConnectionOptions.canSkipAuthentication() ? WebClient.Authentication.TRY_TO_SKIP : WebClient.Authentication.ENFORCE
            ).thenAccept(client -> Platform.runLater(() -> {
                if (client.getStatus().equals(WebClient.Status.SUCCESS)) {
                    Dialogs.showInfoNotification(
                            resources.getString("ConnectionsManager.Connection.webServer"),
                            MessageFormat.format(resources.getString("ConnectionsManager.Connection.connectedTo"), serverURI.toString())
                    );
                } else if (client.getStatus().equals(WebClient.Status.FAILED)) {
                    showConnectionError(serverURI.toString(), client.getFailReason().orElse(null));
                }
            }));
        }
    }

    @FXML
    private void onLoginClicked(ActionEvent ignoredEvent) {
        if (client != null) {
            WebClients.removeClient(client);

            WebClients.createClient(
                    client.getApisHandler().getWebServerURI().toString(),
                    WebClient.Authentication.ENFORCE
            ).thenAccept(client -> Platform.runLater(() -> {
                if (client.getStatus().equals(WebClient.Status.SUCCESS)) {
                    Dialogs.showInfoNotification(
                            resources.getString("ConnectionsManager.Connection.login"),
                            MessageFormat.format(
                                    resources.getString("ConnectionsManager.Connection.loginSuccessful"),
                                    client.getApisHandler().getWebServerURI(),
                                    client.getUsername().orElse("")
                            )
                    );
                } else if (client.getStatus().equals(WebClient.Status.FAILED)) {
                    showConnectionError(this.client.getApisHandler().getWebServerURI().toString(), client.getFailReason().orElse(null));
                }
            }));
        }
    }

    @FXML
    private void onLogoutClicked(ActionEvent ignoredEvent) {
        if (client != null) {
            WebClients.removeClient(client);

            // The client may take some time to close, but it must be closed before
            // attempting to create a new connection, so the unauthenticated client
            // is created after 100ms
            new Timer().schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            Platform.runLater(() -> {
                                WebClients.createClient(
                                        client.getApisHandler().getWebServerURI().toString(),
                                        WebClient.Authentication.SKIP
                                ).thenAccept(client -> Platform.runLater(() -> Dialogs.showInfoNotification(
                                        resources.getString("ConnectionsManager.Connection.logout"),
                                        resources.getString(client.getStatus().equals(WebClient.Status.SUCCESS) ?
                                                "ConnectionsManager.Connection.logoutSuccessful" :
                                                "ConnectionsManager.Connection.logoutSuccessfulButNoUnauthenticated"
                                        )
                                )));
                            });
                        }
                    },
                    100
            );
        }
    }

    @FXML
    private void onDisconnectClicked(ActionEvent ignoredEvent) {
        if (client != null) {
            if (client.canBeClosed()) {
                boolean deletionConfirmed = Dialogs.showConfirmDialog(
                        resources.getString("ConnectionsManager.Connection.disconnectClient"),
                        resources.getString("ConnectionsManager.Connection.disconnectClientConfirmation")
                );

                if (deletionConfirmed) {
                    WebClients.removeClient(client);
                }
            } else {
                Dialogs.showMessageDialog(
                        resources.getString("ConnectionsManager.Connection.removeClient"),
                        resources.getString("ConnectionsManager.Connection.closeImages")
                );
            }
        }
    }

    @FXML
    private void onRemoveClicked(ActionEvent ignoredEvent) {
        boolean deletionConfirmed = Dialogs.showConfirmDialog(
                resources.getString("ConnectionsManager.Connection.removeClient"),
                resources.getString("ConnectionsManager.Connection.removeClientConfirmation")
        );

        if (deletionConfirmed) {
            if (client != null) {
                WebClients.removeClient(client);
            }
            ClientsPreferencesManager.removeURI(serverURI);
        }
    }

    private void initUI() throws IOException {
        UiUtilities.loadFXML(this, Connection.class.getResource("connection.fxml"));

        uri.setText(serverURI.toString());
        uri.setGraphic(UiUtilities.createStateNode(client != null));

        if (client == null) {
            buttons.getChildren().removeAll(browse, login, logout, disconnect);
        } else {
            if (client.isAuthenticated() && client.getUsername().isPresent()) {
                uri.setText(String.format("%s (%s)", serverURI, client.getUsername().get()));
            }

            buttons.getChildren().removeAll(connect, client.isAuthenticated() ? login : logout);

            for (URI uri: connectionModel.getOpenedImagesURIs()) {
                imagesContainer.getChildren().add(new Image(client, uri));
            }
        }
    }

    private void setUpListeners() {
        if (client != null) {
            imagesPane.textProperty().bind(Bindings.concat(
                    Bindings.size(connectionModel.getOpenedImagesURIs()),
                    " ",
                    Bindings.when(Bindings.size(connectionModel.getOpenedImagesURIs()).greaterThan(1))
                            .then(resources.getString("ConnectionsManager.Connection.images"))
                            .otherwise(resources.getString("ConnectionsManager.Connection.image"))
            ));

            connectionModel.getOpenedImagesURIs().addListener((SetChangeListener<? super URI>) change -> {
                imagesContainer.getChildren().clear();

                for (URI uri: change.getSet()) {
                    try {
                        imagesContainer.getChildren().add(new Image(client, uri));
                    } catch (IOException e) {
                        logger.error("Error while creating image pane", e);
                    }
                }
            });
        }
    }

    private static void showConnectionError(String uri, WebClient.FailReason failReason) {
        String message;

        if (failReason == null) {
            message = MessageFormat.format(resources.getString("ConnectionsManager.Connection.connectionFailed"), uri);
        } else {
            message = switch (failReason) {
                case ALREADY_CREATING -> MessageFormat.format(resources.getString("ConnectionsManager.Connection.alreadyCreating"), uri);
                case INVALID_URI_FORMAT -> MessageFormat.format(resources.getString("ConnectionsManager.Connection.invalidURI"), uri);
            };
        }

        Dialogs.showErrorMessage(
                resources.getString("ConnectionsManager.Connection.webServer"),
                message
        );
    }
}
