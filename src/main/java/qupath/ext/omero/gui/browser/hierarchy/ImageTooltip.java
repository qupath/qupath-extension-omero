package qupath.ext.omero.gui.browser.hierarchy;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.util.ResourceBundle;

/**
 * A pane that shows the name, a thumbnail, and whether an image is
 * supported by the extension.
 * <p>
 * An instance of this class must be {@link #close() closed} once no longer used.
 */
class ImageTooltip extends VBox implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ImageTooltip.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final String INVALID_CLASS_NAME = "invalid-image";
    private final Client client;
    private final ChangeListener<? super PixelApi> pixelApiListener;
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
     * @param client the client to use when making requests
     * @throws IOException if an error occurs while creating the tooltip
     */
    public ImageTooltip(Image image, Client client) throws IOException {
        logger.trace("Creating image tooltip for {}", image);
        this.client = client;

        UiUtilities.loadFXML(this, ImageTooltip.class.getResource("image_tooltip.fxml"));

        pixelApiListener = (p, o, n) -> Platform.runLater(() ->
                setErrorLine(image, n)
        );
        pixelApiListener.changed(client.getSelectedPixelApi(), null, client.getSelectedPixelApi().get());
        client.getSelectedPixelApi().addListener(pixelApiListener);

        client.getApisHandler().getThumbnail(image.getId()).whenComplete((thumbnail, error) -> Platform.runLater(() -> {
            if (thumbnail == null) {
                logger.error("Error when retrieving thumbnail of image with ID {}. Cannot set canvas of image tooltip", image.getId(), error);
                return;
            }

            logger.trace("Retrieved thumbnail {} of image with ID {}. Setting to canvas of image tooltip", thumbnail, image.getId());
            UiUtilities.paintBufferedImageOnCanvas(thumbnail, canvas);
        }));
    }

    @Override
    public void close() {
        client.getSelectedPixelApi().removeListener(pixelApiListener);
    }

    private void setErrorLine(Image image, PixelApi pixelApi) {
        getChildren().remove(errorContainer);
        errors.getChildren().clear();

        if (!image.isSupported(pixelApi)) {
            getChildren().add(errorContainer);

            for (Image.UnsupportedReason reason: image.getUnsupportedReasons(pixelApi)) {
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
