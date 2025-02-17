package qupath.ext.omero.gui.browser.hierarchy;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.util.ResourceBundle;

/**
 * A pane that shows the name, a thumbnail, and whether an image is
 * supported by the extension.
 */
class ImageTooltip extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(ImageTooltip.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final String INVALID_CLASS_NAME = "invalid-image";
    @FXML
    private Canvas canvas;
    @FXML
    private VBox errorContainer;
    @FXML
    private VBox errors;

    /**
     * Creates the ImageTooltip.
     *
     * @param image the image to describe
     * @param apisHandler the apis handler to use when making requests
     * @throws IOException if an error occurs while creating the tooltip
     */
    public ImageTooltip(Image image, ApisHandler apisHandler) throws IOException {
        UiUtilities.loadFXML(this, ImageTooltip.class.getResource("image_tooltip.fxml"));

        setErrorLine(image);
        image.isSupported().addListener(change -> Platform.runLater(() -> setErrorLine(image)));

        apisHandler.getThumbnail(image.getId())
                .exceptionally(error -> {
                    logger.error("Error when retrieving thumbnail", error);
                    return null;
                })
                .thenAccept(thumbnail -> Platform.runLater(() -> {
                    if (thumbnail != null) {
                        UiUtilities.paintBufferedImageOnCanvas(thumbnail, canvas);
                    }
                }));
    }

    private void setErrorLine(Image image) {
        getChildren().remove(errorContainer);
        errors.getChildren().clear();

        if (!image.isSupported().get()) {
            getChildren().add(errorContainer);

            for (Image.UnsupportedReason reason: image.getUnsupportedReasons()) {
                Label error = new Label(switch (reason) {
                    case NUMBER_OF_CHANNELS -> resources.getString("Browser.ServerBrowser.Hierarchy.numberOfChannels");
                    case PIXEL_TYPE -> resources.getString("Browser.ServerBrowser.Hierarchy.pixelType");
                    case PIXEL_API_UNAVAILABLE -> resources.getString("Browser.ServerBrowser.Hierarchy.pixelAPI");
                });
                error.getStyleClass().add(INVALID_CLASS_NAME);

                errors.getChildren().add(error);
            }
        }
    }
}
