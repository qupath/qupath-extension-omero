package qupath.ext.omero.gui.connectionsmanager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.net.URI;
import java.util.ResourceBundle;

/**
 * Label showing whether an image is accessible.
 */
class Image extends HBox {

    private static final Logger logger = LoggerFactory.getLogger(Image.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    @FXML
    private Label name;
    @FXML
    private Canvas thumbnail;

    /**
     * Creates an Image label.
     *
     * @param apisHandler the apis handler to use when making requests
     * @param imageUri the URI of the image
     * @throws IOException if an error occurs while creating the window
     */
    public Image(ApisHandler apisHandler, URI imageUri) throws IOException {
        UiUtilities.loadFXML(this, Image.class.getResource("image.fxml"));

        var imageID = ApisHandler.parseEntityId(imageUri);
        if (imageID.isPresent()) {
            apisHandler.getImage(imageID.getAsLong())
                    .whenComplete((image, error) -> {
                        if (image == null) {
                            logger.error("Error when retrieving image of ID {}", imageID.getAsLong(), error);
                        } else {
                            Platform.runLater(() -> name.setText(image.getLabel()));
                        }
                    });

            apisHandler.getThumbnail(imageID.getAsLong(), (int) thumbnail.getWidth())
                    .whenComplete((thumbnail, error) -> {
                        if (thumbnail == null) {
                            logger.error("Error when retrieving thumbnail", error);
                        } else {
                            Platform.runLater(() -> UiUtilities.paintBufferedImageOnCanvas(thumbnail, this.thumbnail));
                        }
                    });
        }

        apisHandler.isLinkReachable(imageUri, RequestSender.RequestType.GET).whenComplete((v, error) -> {
            if (error == null) {
                setStatus(imageUri.toString(), true);
            } else {
                logger.debug("Cannot reach {}", imageUri, error);
                setStatus(resources.getString("ConnectionsManager.Image.unreachableImage"), false);
            }
        });
    }

    private void setStatus(String text, boolean isActive) {
        Platform.runLater(() -> {
            name.setTooltip(new Tooltip(text));
            name.setGraphic(UiUtilities.createStateNode(isActive));
        });
    }
}
