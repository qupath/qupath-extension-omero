package qupath.ext.omero.gui.connectionsmanager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
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
    private static final ResourceBundle resources = Utils.getResources();
    private final URI imageUri;
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
        this.imageUri = imageUri;
        logger.debug("Creating image label of {}", imageUri);

        UiUtilities.loadFXML(this, Image.class.getResource("image.fxml"));

        var imageID = ApisHandler.parseEntityId(imageUri);
        if (imageID.isPresent()) {
            apisHandler.getImage(imageID.get())
                    .whenComplete((image, error) -> {
                        if (image == null) {
                            logger.error("Error when retrieving image of ID {}", imageID.get(), error);
                        } else {
                            Platform.runLater(() -> name.setText(image.getLabel()));
                        }
                    });

            apisHandler.getThumbnail(imageID.get(), (int) thumbnail.getWidth())
                    .whenComplete((thumbnail, error) -> {
                        if (thumbnail == null) {
                            logger.error("Error when retrieving thumbnail", error);
                        } else {
                            Platform.runLater(() -> UiUtilities.paintBufferedImageOnCanvas(thumbnail, this.thumbnail));
                        }
                    });
        } else {
            logger.debug("Cannot find image ID in {}. This image label won't show any image", imageUri);
        }

        apisHandler.isLinkReachable(imageUri, RequestSender.RequestType.GET).whenComplete((v, error) -> {
            if (error == null) {
                setStatus(imageUri.toString(), true);
            } else {
                logger.debug("Cannot reach {}. Considering this image not reachable", imageUri, error);
                setStatus(resources.getString("ConnectionsManager.Image.unreachableImage"), false);
            }
        });
    }

    /**
     * @return the URI of the image shown
     */
    public URI getImageUri() {
        return imageUri;
    }

    private void setStatus(String text, boolean isActive) {
        Platform.runLater(() -> {
            name.setTooltip(new Tooltip(text));
            name.setGraphic(UiUtilities.createStateNode(isActive));
        });
    }
}
