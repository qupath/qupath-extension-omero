package qupath.ext.omero.gui.browser;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.controlsfx.control.textfield.CustomTextField;
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
    private CustomTextField webJpegQuality;
    @FXML
    private TextField omeroAddress;
    @FXML
    private TextField omeroPort;
    @FXML
    private TextField msPixelBufferAPIPort;

    /**
     * Creates the settings window.
     *
     * @param ownerWindow the stage who should own this window
     * @param client the client whose settings should be displayed
     * @throws IOException if an error occurs while creating the window
     */
    public Settings(Stage ownerWindow, Client client) throws IOException {
        logger.debug("Creating settings window for {}", client);

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
        logger.debug("Resetting entries to values of pixel APIs");

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

        UnaryOperator<TextFormatter.Change> floatFilter = change ->
                Pattern.matches("^\\d*\\.?\\d*$", change.getControlNewText()) ? change : null;
        webJpegQuality.setTextFormatter(new TextFormatter<>(floatFilter));
        webJpegQuality.rightProperty().bind(Bindings.createObjectBinding(
                () -> {
                    logger.trace("Setting right icon for web JPEG quality");
                    try {
                        float quality = Float.parseFloat(webJpegQuality.getText());
                        if (quality >= 0 && quality <= 1) {
                            logger.trace("Web JPEG quality {} between 0 and 1. No error icon to display", quality);
                            return null;
                        } else {
                            logger.trace("JPEG quality {} not between 0 and 1. Showing error icon", quality);
                            return new Label("❌");
                        }
                    } catch (NumberFormatException e) {
                        logger.trace("Cannot convert JPEG quality {} to float. Showing error icon", webJpegQuality.getText(), e);
                        return new Label("❌");
                    }
                },
                webJpegQuality.textProperty()
        ));

        UnaryOperator<TextFormatter.Change> integerFilter = change ->
                Pattern.matches("^\\d*$", change.getControlNewText()) ? change : null;
        omeroPort.setTextFormatter(new TextFormatter<>(integerFilter));
        msPixelBufferAPIPort.setTextFormatter(new TextFormatter<>(integerFilter));

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
                            logger.debug("Enter key pressed. Saving settings");
                            onOKClicked(null);
                            break;
                        case ESCAPE:
                            logger.debug("Escape key pressed. Closing settings window");
                            close();
                            break;
                    }
                }
        );
    }

    private boolean save() {
        logger.debug("Saving settings");

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

        logger.debug("Settings saved");
        Dialogs.showInfoNotification(
                resources.getString("Browser.ServerBrowser.Settings.saved"),
                resources.getString("Browser.ServerBrowser.Settings.parametersSaved")
        );
        return true;
    }
}
