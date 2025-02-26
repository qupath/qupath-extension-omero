package qupath.ext.omero.gui.browser;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.pixelapis.ice.IceApi;
import qupath.ext.omero.core.pixelapis.mspixelbuffer.MsPixelBufferApi;
import qupath.ext.omero.core.pixelapis.web.WebApi;
import qupath.ext.omero.gui.UiUtilities;
import qupath.fx.dialogs.Dialogs;

import java.io.IOException;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Window allowing to change settings related to a {@link Client}.
 */
class Settings extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(Settings.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final MsPixelBufferApi msPixelBufferApi;
    private final WebApi webApi;
    private final IceApi iceApi;
    @FXML
    private TextField msPixelBufferAPIPort;
    @FXML
    private TextField webJpegQuality;
    @FXML
    private TextField omeroAddress;
    @FXML
    private TextField omeroPort;

    /**
     * Creates the settings window.
     *
     * @param ownerWindow the stage who should own this window
     * @param client the client whose settings should be displayed
     * @throws IOException if an error occurs while creating the window
     */
    public Settings(Stage ownerWindow, Client client) throws IOException {
        this.msPixelBufferApi = client.getPixelAPI(MsPixelBufferApi.class);
        this.webApi = client.getPixelAPI(WebApi.class);
        this.iceApi = client.getPixelAPI(IceApi.class);

        initUI(ownerWindow);
        setUpListeners();
    }

    /**
     * Reset the text fields of this window to the values of the pixel APIs
     */
    public void resetEntries() {
        msPixelBufferAPIPort.setText(String.valueOf(msPixelBufferApi.getPort().get()));
        webJpegQuality.setText(String.valueOf(webApi.getJpegQuality().get()));
        omeroAddress.setText(iceApi.getServerAddress().get());
        omeroPort.setText(String.valueOf(iceApi.getServerPort().get()));
    }

    @FXML
    private void onOKClicked(ActionEvent ignoredEvent) {
        if (save()) {
            close();
        }
    }

    @FXML
    private void onCancelClicked(ActionEvent ignoredEvent) {
        close();
    }

    private void initUI(Stage ownerWindow) throws IOException {
        UiUtilities.loadFXML(this, Settings.class.getResource("settings.fxml"));

        UnaryOperator<TextFormatter.Change> integerFilter = change ->
                Pattern.matches("^\\d*$", change.getControlNewText()) ? change : null;
        msPixelBufferAPIPort.setTextFormatter(new TextFormatter<>(integerFilter));
        omeroPort.setTextFormatter(new TextFormatter<>(integerFilter));

        UnaryOperator<TextFormatter.Change> floatFilter = change ->
                Pattern.matches("^\\d*\\.?\\d*$", change.getControlNewText()) ? change : null;
        webJpegQuality.setTextFormatter(new TextFormatter<>(floatFilter));

        resetEntries();

        initOwner(ownerWindow);
        show();
    }

    private void setUpListeners() {
        msPixelBufferApi.getPort().addListener((p, o, n) -> Platform.runLater(() ->
                msPixelBufferAPIPort.setText(String.valueOf(n))
        ));
        webApi.getJpegQuality().addListener((p, o, n) -> Platform.runLater(() ->
                webJpegQuality.setText(String.valueOf(n))
        ));
        iceApi.getServerAddress().addListener((p, o, n) -> Platform.runLater(() ->
                omeroAddress.setText(n)
        ));
        iceApi.getServerPort().addListener((p, o, n) -> Platform.runLater(() ->
                omeroPort.setText(String.valueOf(n))
        ));

        getScene().addEventFilter(
                KeyEvent.KEY_PRESSED,
                keyEvent -> {
                    switch (keyEvent.getCode()) {
                        case ENTER:
                            onOKClicked(null);
                            break;
                        case ESCAPE:
                            close();
                            break;
                    }
                }
        );
    }

    private boolean save() {
        try {
            webApi.setJpegQuality(Float.parseFloat(webJpegQuality.getText()));
        } catch (IllegalArgumentException e) {
            logger.warn("Incorrect JPEG quality {}", webJpegQuality.getText(), e);

            Dialogs.showErrorMessage(
                    resources.getString("Browser.ServerBrowser.Settings.error"),
                    resources.getString("Browser.ServerBrowser.Settings.invalidJpegQuality")
            );
            return false;
        }

        iceApi.setServerAddress(omeroAddress.getText());
        try {
            iceApi.setServerPort(Integer.parseInt(omeroPort.getText()));
        } catch (IllegalArgumentException e) {
            logger.warn("Incorrect ICE server port {}", omeroPort.getText(), e);

            Dialogs.showErrorMessage(
                    resources.getString("Browser.ServerBrowser.Settings.error"),
                    resources.getString("Browser.ServerBrowser.Settings.iceServerPort")
            );
            return false;
        }

        try {
            msPixelBufferApi.setPort(Integer.parseInt(msPixelBufferAPIPort.getText()), false);
        } catch (IllegalArgumentException e) {
            logger.warn("Incorrect pixel buffer microservice port {}", msPixelBufferAPIPort.getText(), e);

            Dialogs.showErrorMessage(
                    resources.getString("Browser.ServerBrowser.Settings.error"),
                    resources.getString("Browser.ServerBrowser.Settings.invalidPixelBufferMicroservicePort")
            );
            return false;
        }

        Dialogs.showInfoNotification(
                resources.getString("Browser.ServerBrowser.Settings.saved"),
                resources.getString("Browser.ServerBrowser.Settings.parametersSaved")
        );
        return true;
    }
}
