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
import qupath.ext.omero.core.apis.webclient.SimpleServerEntity;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
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
     * Creates an image label.
     *
     * @param apisHandler the apis handler to use when making requests
     * @param imageUri the URI of the image
     * @throws IOException if an error occurs while creating the window
     */
    public Image(ApisHandler apisHandler, URI imageUri) throws IOException {
        logger.debug("Creating image label for {}", imageUri);
        this.imageUri = imageUri;

        UiUtilities.loadFXML(this, Image.class.getResource("image.fxml"));

        Optional<Long> imageId = ApisHandler.parseEntity(imageUri).map(SimpleServerEntity::id);
        if (imageId.isPresent()) {
            logger.debug("Found image ID {} in {}. Fetching image label and thumbnail", imageId.get(), imageUri);

            apisHandler.getImage(imageId.get()).whenComplete((image, error) -> {
                if (image == null) {
                    logger.error("Error when retrieving image of ID {}. Cannot set label", imageId.get(), error);
                } else {
                    logger.debug("Got {} when retrieving image of ID {}. Setting label", image, imageId);
                    Platform.runLater(() -> name.setText(image.getLabel()));
                }
            });

            apisHandler.getThumbnail(imageId.get(), (int) thumbnail.getWidth()).whenComplete((thumbnail, error) -> {
                if (thumbnail == null) {
                    logger.error("Error when retrieving thumbnail of ID {}. Cannot set thumbnail", imageId, error);
                } else {
                    logger.debug("Got thumbnail {} of image with ID {}. Setting thumbnail", thumbnail, imageId);
                    Platform.runLater(() -> UiUtilities.paintBufferedImageOnCanvas(thumbnail, this.thumbnail));
                }
            });
        } else {
            logger.warn("Cannot find image ID in {}. This image label won't show any image", imageUri);
        }

        logger.debug("Checking if {} is reachable", imageUri);
        apisHandler.isLinkReachable(imageUri, RequestSender.RequestType.GET).whenComplete((v, error) -> {
            if (error == null) {
                logger.debug("Image {} reachable", imageUri);
                setStatus(imageUri.toString(), true);
            } else {
                logger.debug("Cannot reach {}. Considering the image not reachable", imageUri, error);
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
