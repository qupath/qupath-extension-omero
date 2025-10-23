package qupath.ext.omero.gui.browser;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
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
import qupath.ext.omero.core.preferences.PreferencesManager;
import qupath.ext.omero.gui.UiUtils;
import qupath.fx.dialogs.Dialogs;

import java.io.IOException;
import java.net.URI;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Window allowing to change settings related to a {@link Client}.
 * <p>
 * An instance of this class must be {@link #close() closed} once no longer used.
 */
class Settings extends Stage implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(Settings.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final URI webServerUri;
    private final WebApi webApi;
    private final IceApi iceApi;
    private final MsPixelBufferApi msPixelBufferApi;
    private final ChangeListener<? super Number> webJpegQualityListener;
    private final ChangeListener<? super String> omeroAddressListener;
    private final ChangeListener<? super Number> omeroPortListener;
    private final ChangeListener<? super Number> numberOfIceReadersListener;
    private final ChangeListener<? super Number> msPixelBufferAPIPortListener;
    @FXML
    private CustomTextField maxBodySize;
    @FXML
    private CustomTextField webJpegQuality;
    @FXML
    private TextField omeroAddress;
    @FXML
    private TextField omeroPort;
    @FXML
    private Spinner<Integer> numberOfIceReaders;
    @FXML
    private TextField msPixelBufferAPIPort;

    /**
     * Creates the settings window.
     *
     * @param owner the stage who should own this window
     * @param client the client whose settings should be displayed
     * @throws IOException if an error occurs while creating the window
     */
    public Settings(Stage owner, Client client) throws IOException {
        logger.debug("Creating settings window for {}", client);

        this.webServerUri = client.getApisHandler().getWebServerUri();
        this.webApi = client.getPixelAPI(WebApi.class);
        this.iceApi = client.getPixelAPI(IceApi.class);
        this.msPixelBufferApi = client.getPixelAPI(MsPixelBufferApi.class);
        this.webJpegQualityListener = (p, o, n) -> Platform.runLater(() ->
                webJpegQuality.setText(String.valueOf(n))
        );
        this.omeroAddressListener = (p, o, n) -> Platform.runLater(() ->
                omeroAddress.setText(n)
        );
        this.omeroPortListener = (p, o, n) -> Platform.runLater(() ->
                omeroPort.setText(String.valueOf(n))
        );
        this.numberOfIceReadersListener = (p, o, n) -> Platform.runLater(() ->
                numberOfIceReaders.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        iceApi.getMinNumberOfReaders(),
                        iceApi.getMaxNumberOfReaders(),
                        n.intValue()
                ))
        );
        this.msPixelBufferAPIPortListener = (p, o, n) -> Platform.runLater(() ->
                msPixelBufferAPIPort.setText(String.valueOf(n))
        );

        UiUtils.loadFXML(this, Settings.class.getResource("settings.fxml"));

        UnaryOperator<TextFormatter.Change> floatFilter = change ->
                Pattern.matches("^\\d*\\.?\\d*$", change.getControlNewText()) ? change : null;

        maxBodySize.setTextFormatter(new TextFormatter<>(floatFilter));
        maxBodySize.rightProperty().bind(Bindings.createObjectBinding(
                () -> {
                    logger.trace("Setting right icon for max body size");
                    try {
                        float maxBodySize = Float.parseFloat(this.maxBodySize.getText());
                        if (maxBodySize > 0) {
                            logger.trace("Max body size {} greater than 0. No error icon to display", maxBodySize);
                            return null;
                        } else {
                            logger.trace("Max body size {} lower than or equal to 0. Showing error icon", maxBodySize);
                            return new Label("❌");
                        }
                    } catch (NumberFormatException e) {
                        logger.trace("Cannot convert max body size {} to float. Showing error icon", maxBodySize.getText(), e);
                        return new Label("❌");
                    }
                },
                maxBodySize.textProperty()
        ));

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

        webApi.getJpegQuality().addListener(webJpegQualityListener);
        iceApi.getServerAddress().addListener(omeroAddressListener);
        iceApi.getServerPort().addListener(omeroPortListener);
        iceApi.getNumberOfReaders().addListener(numberOfIceReadersListener);
        msPixelBufferApi.getPort().addListener(msPixelBufferAPIPortListener);

        getScene().addEventFilter(
                KeyEvent.KEY_PRESSED,
                keyEvent -> {
                    switch (keyEvent.getCode()) {
                        case ENTER:
                            logger.debug("Enter key pressed. Saving settings and hiding settings window");
                            if (save()) {
                                hide();
                            }
                            break;
                        case ESCAPE:
                            logger.debug("Escape key pressed. Hiding settings window");
                            hide();
                            break;
                    }
                }
        );

        initOwner(owner);
        show();
    }

    @Override
    public void close() {
        webApi.getJpegQuality().removeListener(webJpegQualityListener);
        iceApi.getServerAddress().removeListener(omeroAddressListener);
        iceApi.getServerPort().removeListener(omeroPortListener);
        iceApi.getNumberOfReaders().removeListener(numberOfIceReadersListener);
        msPixelBufferApi.getPort().removeListener(msPixelBufferAPIPortListener);
    }

    /**
     * Reset the text fields of this window to the values of the pixel APIs
     */
    public void resetEntries() {
        logger.debug("Resetting entries to values of pixel APIs");

        maxBodySize.setText(String.valueOf(PreferencesManager.getMaxBodySizeBytes(webServerUri) / 1000000d));
        webJpegQuality.setText(String.valueOf(webApi.getJpegQuality().get()));
        omeroAddress.setText(iceApi.getServerAddress().get());
        omeroPort.setText(String.valueOf(iceApi.getServerPort().get()));
        numberOfIceReaders.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                iceApi.getMinNumberOfReaders(),
                iceApi.getMaxNumberOfReaders(),
                iceApi.getNumberOfReaders().get()
        ));
        msPixelBufferAPIPort.setText(String.valueOf(msPixelBufferApi.getPort().get()));
    }

    @FXML
    private void onOKClicked(ActionEvent ignoredEvent) {
        if (save()) {
            hide();
        }
    }

    @FXML
    private void onCancelClicked(ActionEvent ignoredEvent) {
        hide();
    }

    private boolean save() {
        logger.debug("Saving settings");

        try {
            PreferencesManager.setMaxBodySizeBytes(webServerUri, (long) (Float.parseFloat(maxBodySize.getText()) * 1000000));
        } catch (IllegalArgumentException e) {
            logger.warn("Incorrect max body size {}", maxBodySize.getText(), e);

            Dialogs.showErrorMessage(
                    resources.getString("Browser.ServerBrowser.Settings.error"),
                    resources.getString("Browser.ServerBrowser.Settings.invalidMaxBodySize")
            );
            return false;
        }

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
            iceApi.setNumberOfReaders(numberOfIceReaders.getValue());
        } catch (IllegalArgumentException e) {
            logger.warn("Incorrect ICE server number of readers {}", numberOfIceReaders.getValue(), e);

            Dialogs.showErrorMessage(
                    resources.getString("Browser.ServerBrowser.Settings.error"),
                    resources.getString("Browser.ServerBrowser.Settings.iceNumberOfReaders")
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
