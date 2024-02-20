package qupath.ext.omero.gui.browser.serverbrowser.settings;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.pixelapis.ice.IceAPI;
import qupath.ext.omero.core.pixelapis.mspixelbuffer.MsPixelBufferAPI;
import qupath.ext.omero.core.pixelapis.web.WebAPI;
import qupath.ext.omero.gui.UiUtilities;
import qupath.fx.dialogs.Dialogs;

import java.io.IOException;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Window allowing to change settings related to a {@link WebClient}.
 */
public class Settings extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(Settings.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final MsPixelBufferAPI msPixelBufferAPI;
    private final WebAPI webAPI;
    private final IceAPI iceAPI;
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
     * @param ownerWindow  the stage who should own this window
     * @param client  the client whose settings should be displayed
     * @throws IOException if an error occurs while creating the window
     */
    public Settings(Stage ownerWindow, WebClient client) throws IOException {
        this.msPixelBufferAPI = client.getPixelAPI(MsPixelBufferAPI.class);
        this.webAPI = client.getPixelAPI(WebAPI.class);
        this.iceAPI = client.getPixelAPI(IceAPI.class);

        initUI(ownerWindow);
        setUpListeners();
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

    @FXML
    private void onApplyClicked(ActionEvent ignoredEvent) {
        save();
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

        msPixelBufferAPIPort.setText(String.valueOf(msPixelBufferAPI.getPort().get()));
        webJpegQuality.setText(String.valueOf(webAPI.getJpegQuality().get()));
        omeroAddress.setText(iceAPI.getServerAddress().get());
        omeroPort.setText(String.valueOf(iceAPI.getServerPort().get()));

        initOwner(ownerWindow);
        show();
    }

    private void setUpListeners() {
        msPixelBufferAPI.getPort().addListener((p, o, n) -> Platform.runLater(() ->
                msPixelBufferAPIPort.setText(String.valueOf(n))
        ));
        webAPI.getJpegQuality().addListener((p, o, n) -> Platform.runLater(() ->
                webJpegQuality.setText(String.valueOf(n))
        ));
        iceAPI.getServerAddress().addListener((p, o, n) -> Platform.runLater(() ->
                omeroAddress.setText(n)
        ));
        iceAPI.getServerPort().addListener((p, o, n) -> Platform.runLater(() ->
                omeroPort.setText(String.valueOf(n))
        ));
    }

    private boolean save() {
        try {
            msPixelBufferAPI.setPort(Integer.parseInt(msPixelBufferAPIPort.getText()), false);
            webAPI.setJpegQuality(Float.parseFloat(webJpegQuality.getText()));
            iceAPI.setServerAddress(omeroAddress.getText());
            iceAPI.setServerPort(Integer.parseInt(omeroPort.getText()));

            // Reset the texts, as the user input may have had incorrect values and been ignored
            msPixelBufferAPIPort.setText(String.valueOf(msPixelBufferAPI.getPort().get()));
            webJpegQuality.setText(String.valueOf(webAPI.getJpegQuality().get()));
            omeroAddress.setText(iceAPI.getServerAddress().get());
            omeroPort.setText(String.valueOf(iceAPI.getServerPort().get()));

            Dialogs.showInfoNotification(
                    resources.getString("Browser.ServerBrowser.Settings.saved"),
                    resources.getString("Browser.ServerBrowser.Settings.parametersSaved")
            );

            return true;
        } catch (NumberFormatException e) {
            logger.warn("Incorrect input", e);
            return false;
        }
    }
}
