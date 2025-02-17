package qupath.ext.omero.gui.login;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.ext.omero.gui.UiUtilities;
import qupath.fx.dialogs.Dialogs;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * A modal form to connect to an OMERO server. It can be closed by pressing the ESCAPE key.
 */
public class LoginForm extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(LoginForm.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final String DEFAULT_URL = "https://idr.openmicroscopy.org/";
    private final Consumer<Client> onClientCreated;
    private Client createdClient;
    @FXML
    private TextField url;
    @FXML
    private CheckBox publicUser;
    @FXML
    private GridPane usernamePassword;
    @FXML
    private TextField username;
    @FXML
    private PasswordField password;

    /**
     * Create the form. It will allow the user to connect to a new OMERO server.
     *
     * @param owner the window this form should be modal to
     * @param onClientCreated a function that will be called upon the successful creation of a client. It may be
     *                        executed from any thread
     * @throws IOException if an error occurs while getting the FXML file of this form
     */
    public LoginForm(Window owner, Consumer<Client> onClientCreated) throws IOException {
        this(owner, null, null, onClientCreated);
    }


    /**
     * Create the form. It will allow the user to connect to the provided OMERO server.
     *
     * @param owner the window this form should be modal to
     * @param webServerUri the URI of the OMERO web server to connect to
     * @param credentials some credentials to pre-fill the form. Can be null
     * @param onClientCreated a function that will be called upon the successful creation of a client. It may be
     *                        executed from any thread
     * @throws IOException if an error occurs while getting the FXML file of this form
     */
    public LoginForm(Window owner, URI webServerUri, Credentials credentials, Consumer<Client> onClientCreated) throws IOException {
        this.onClientCreated = onClientCreated;

        UiUtilities.loadFXML(this, LoginForm.class.getResource("login_form.fxml"));

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);

        if (webServerUri == null) {
            url.setText(PreferencesManager.getServerPreferences().isEmpty() ?
                    DEFAULT_URL :
                    PreferencesManager.getServerPreferences().getLast().webServerUri().toString()
            );
        } else {
            url.setText(webServerUri.toString());
            url.setDisable(true);
        }

        if (credentials != null) {
            publicUser.setSelected(credentials.userType().equals(Credentials.UserType.PUBLIC_USER));

            if (credentials.userType().equals(Credentials.UserType.REGULAR_USER)) {
                username.setText(credentials.username());
            }
        }

        usernamePassword.visibleProperty().bind(publicUser.selectedProperty().not());
        usernamePassword.managedProperty().bind(usernamePassword.visibleProperty());
        usernamePassword.visibleProperty().addListener((p, o, n) -> sizeToScene());

        getScene().addEventFilter(
                KeyEvent.KEY_PRESSED,
                keyEvent -> {
                    switch (keyEvent.getCode()) {
                        case ENTER:
                            onConnectClicked(null);
                            break;
                        case ESCAPE:
                            close();
                            break;
                    }
                }
        );
    }

    /**
     * @return the last client that was created with this form, or an empty Optional if no client was
     * created yet
     */
    public Optional<Client> getCreatedClient() {
        return Optional.ofNullable(createdClient);
    }

    @FXML
    private void onConnectClicked(ActionEvent ignoredEvent) {
        if (!publicUser.isSelected() && (username.getText().isBlank() || password.getText().isBlank())) {
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    this,
                    MessageFormat.format(
                            resources.getString("Login.LoginForm.connectingTo"),
                            url.getText()
                    ),
                    executor::shutdownNow
            );
        } catch (IOException e) {
            logger.error("Error while creating waiting window", e);
            executor.shutdown();
            return;
        }
        waitingWindow.initOwner(this);
        waitingWindow.show();

        executor.execute(() -> {
            try {
                Credentials credentials = publicUser.isSelected() ? new Credentials() : new Credentials(username.getText(), getPassword());
                Client client = Client.createOrGet(url.getText(), credentials);
                onClientCreated.accept(client);

                Platform.runLater(() -> {
                    createdClient = client;
                    waitingWindow.close();
                    close();
                });
            } catch (Exception e) {
                logger.error("Error while creating connection to {}", url.getText(), e);

                Platform.runLater(() -> {
                    waitingWindow.close();

                    if (!(e instanceof InterruptedException)) {
                        new Dialogs.Builder()
                                .alertType(Alert.AlertType.ERROR)
                                .title(resources.getString("Login.LoginForm.connectionFailed"))
                                .content(new Label(MessageFormat.format(
                                        resources.getString("Login.LoginForm.connectionFailedCheck"),
                                        url.getText()
                                )))
                                .owner(this)
                                .show();
                    }
                });
            }
        });
        executor.shutdown();
    }

    private char[] getPassword() {
        int passwordLength = password.getCharacters().length();
        char[] passwordContent = new char[passwordLength];

        for (int i = 0; i < passwordLength; i++) {
            passwordContent[i] = password.getCharacters().charAt(i);
        }

        return passwordContent;
    }
}
