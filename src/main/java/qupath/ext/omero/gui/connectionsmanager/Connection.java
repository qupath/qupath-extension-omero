package qupath.ext.omero.gui.connectionsmanager;

import javafx.application.Platform;
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
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.gui.login.LoginForm;
import qupath.fx.dialogs.Dialogs;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Pane displaying the status of a connection to a server as well as buttons to
 * connect, log in, log out, or remove the connection.
 * <p>
 * It also displays the list of images of this server that are currently opened using the
 * {@link Image Image} label.
 */
class Connection extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(Connection.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final Client client;
    private final URI serverURI;
    private final Consumer<Client> openClientBrowser;
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
    private Button disconnect;
    @FXML
    private Button remove;
    @FXML
    private TitledPane imagesPane;
    @FXML
    private VBox imagesContainer;

    /**
     * Creates the connection pane using a {@link Client}. The user will have the possibility
     * to log in, log out, or remove the connection, but not to connect to the server.
     *
     * @param client the client corresponding to the connection with the server
     * @param openClientBrowser a function that will be called to request opening the browser of a client
     * @throws IOException if an error occurs while creating the pane
     */
    public Connection(Client client, Consumer<Client> openClientBrowser) throws IOException {
        this(client, client.getApisHandler().getWebServerURI(), openClientBrowser);
    }

    /**
     * Creates the connection pane using the URI of a server. The user will have the possibility
     * to connect to the server or to remove the connection, but not to log in or log out.
     * Logging in or logging out will only be possible after a connection to the server is made.
     *
     * @param serverURI the URI of the server
     * @param openClientBrowser a function that will be called to request opening the browser of a client
     * @throws IOException if an error occurs while creating the pane
     */
    public Connection(URI serverURI, Consumer<Client> openClientBrowser) throws IOException {
        this(null, serverURI, openClientBrowser);
    }

    private Connection(Client client, URI serverURI, Consumer<Client> openClientBrowser) throws IOException {
        this.client = client;
        this.serverURI = serverURI;
        this.openClientBrowser = openClientBrowser;

        initUI();
        setUpListeners();
    }

    @FXML
    private void onBrowseClicked(ActionEvent ignoredEvent) {
        if (client != null) {
            openClientBrowser.accept(client);
        }
    }

    @FXML
    private void onConnectClicked(ActionEvent ignoredEvent) {
        try {
            new LoginForm(
                    getScene().getWindow(),
                    serverURI,
                    PreferencesManager.getCredentials(serverURI).orElse(null),
                    client -> Platform.runLater(() -> Dialogs.showInfoNotification(
                            resources.getString("ConnectionsManager.Connection.omeroServer"),
                            MessageFormat.format(
                                    resources.getString("ConnectionsManager.Connection.connectedTo"),
                                    serverURI.toString(),
                                    client.getApisHandler().getCredentials().username() == null ?
                                            resources.getString("ConnectionsManager.Connection.publicUser") :
                                            client.getApisHandler().getCredentials().username()
                            )
                    ))
            ).show();
        } catch (IOException e) {
            logger.error("Error while creating login form", e);
        }
    }

    @FXML
    private void onLoginClicked(ActionEvent ignoredEvent) {
        if (client != null && !client.canBeClosed()) {
            Dialogs.showMessageDialog(
                    resources.getString("ConnectionsManager.Connection.login"),
                    resources.getString("ConnectionsManager.Connection.closeImages")
            );
            return;
        }

        try {
            new LoginForm(
                    getScene().getWindow(),
                    serverURI,
                    null,
                    client -> Platform.runLater(() -> Dialogs.showInfoNotification(
                            resources.getString("ConnectionsManager.Connection.omeroServer"),
                            MessageFormat.format(
                                    resources.getString("ConnectionsManager.Connection.connectedTo"),
                                    serverURI.toString(),
                                    client.getApisHandler().getCredentials().username() == null ?
                                            resources.getString("ConnectionsManager.Connection.publicUser") :
                                            client.getApisHandler().getCredentials().username()
                            )
                    ))
            ).show();
        } catch (IOException e) {
            logger.error("Error while creating login form", e);
        }
    }

    @FXML
    private void onDisconnectClicked(ActionEvent ignoredEvent) {
        if (client == null) {
            return;
        }

        if (!client.canBeClosed()) {
            Dialogs.showMessageDialog(
                    resources.getString("ConnectionsManager.Connection.removeClient"),
                    resources.getString("ConnectionsManager.Connection.closeImages")
            );
            return;
        }

        if (!Dialogs.showConfirmDialog(
                resources.getString("ConnectionsManager.Connection.disconnectClient"),
                resources.getString("ConnectionsManager.Connection.disconnectClientConfirmation")
        )) {
            return;
        }

        try {
            client.close();
        } catch (Exception e) {
            logger.error("Error when closing client {}", client.getApisHandler().getWebServerURI(), e);
        }
    }

    @FXML
    private void onRemoveClicked(ActionEvent ignoredEvent) {
        if (client != null && !client.canBeClosed()) {
            Dialogs.showMessageDialog(
                    resources.getString("ConnectionsManager.Connection.removeClient"),
                    resources.getString("ConnectionsManager.Connection.closeImages")
            );
            return;
        }

        if (!Dialogs.showConfirmDialog(
                resources.getString("ConnectionsManager.Connection.removeClient"),
                resources.getString("ConnectionsManager.Connection.removeClientConfirmation")
        )) {
            return;
        }

        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.error("Error when closing client {}", client.getApisHandler().getWebServerURI(), e);
            }
        }
        PreferencesManager.removeServer(serverURI);
    }

    private void initUI() throws IOException {
        UiUtilities.loadFXML(this, Connection.class.getResource("connection.fxml"));

        if (client == null) {
            uri.setText(serverURI.toString());
            uri.setGraphic(UiUtilities.createStateNode(false));

            buttons.getChildren().removeAll(browse, login, disconnect);
        } else {
            uri.setText(switch (client.getApisHandler().getCredentials().userType()) {
                case PUBLIC_USER -> serverURI.toString();
                case REGULAR_USER -> String.format("%s (%s)", serverURI, client.getApisHandler().getCredentials().username());
            });
            uri.setGraphic(UiUtilities.createStateNode(true));

            buttons.getChildren().remove(connect);
            if (client.getApisHandler().getCredentials().userType().equals(Credentials.UserType.REGULAR_USER)) {
                buttons.getChildren().remove(login);
            }

            imagesPane.setText(String.format(
                    "%d %s",
                    client.getOpenedImagesURIs().size(),
                    resources.getString(client.getOpenedImagesURIs().size() > 1 ?
                            "ConnectionsManager.Connection.images" :
                            "ConnectionsManager.Connection.image"
                    )
            ));

            for (URI uri: client.getOpenedImagesURIs()) {
                imagesContainer.getChildren().add(new Image(client.getApisHandler(), uri));
            }
        }
    }

    private void setUpListeners() {
        if (client != null) {
            client.getOpenedImagesURIs().addListener((SetChangeListener<? super URI>) change -> Platform.runLater(() -> {
                imagesPane.setText(String.format(
                        "%d %s",
                        client.getOpenedImagesURIs().size(),
                        resources.getString(client.getOpenedImagesURIs().size() > 1 ?
                                        "ConnectionsManager.Connection.images" :
                                        "ConnectionsManager.Connection.image"
                        )
                ));

                imagesContainer.getChildren().clear();
                for (URI uri: client.getOpenedImagesURIs()) {
                    try {
                        imagesContainer.getChildren().add(new Image(client.getApisHandler(), uri));
                    } catch (IOException e) {
                        logger.error("Error while creating image pane", e);
                    }
                }
            }));
        }
    }
}
